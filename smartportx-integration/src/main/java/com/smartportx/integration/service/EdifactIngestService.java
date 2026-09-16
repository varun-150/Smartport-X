package com.smartportx.integration.service;

import com.smartportx.common.correlation.CorrelationContext;
import com.smartportx.common.error.ConflictException;
import com.smartportx.common.error.NotFoundException;
import com.smartportx.common.event.DomainEvent;
import com.smartportx.integration.domain.EdifactMessage;
import com.smartportx.integration.domain.EdifactStatus;
import com.smartportx.integration.domain.events.IntegrationDomainEvents;
import com.smartportx.integration.event.EventTransport;
import com.smartportx.integration.parser.EdifactContainer;
import com.smartportx.integration.parser.EdifactParser;
import com.smartportx.integration.parser.ParsedEdifact;
import com.smartportx.integration.repository.EdifactMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound EDIFACT orchestration (M8.3/M8.5). Parses a COPRAR/COPARN message, persists it
 * with a lifecycle (RECEIVED -> PARSED -> PROCESSED / ERROR), guards against duplicate
 * processing by (sender, receiver, interchange_ref, message_ref), emits domain events and
 * wraps the persist/process step in a retry with a dead-letter (ERROR) recovery path.
 */
@Service
public class EdifactIngestService {

    private static final Logger log = LoggerFactory.getLogger(EdifactIngestService.class);

    private final EdifactParser parser;
    private final EdifactMessageRepository messages;
    private final EventTransport transport;
    private final IntegrationAuditService audit;
    private final String source;

    public EdifactIngestService(EdifactParser parser,
                                EdifactMessageRepository messages,
                                EventTransport transport,
                                IntegrationAuditService audit,
                                @Value("${smartportx.integration.source:smartportx-integration}") String source) {
        this.parser = parser;
        this.messages = messages;
        this.transport = transport;
        this.audit = audit;
        this.source = source;
    }

    /**
     * Parse + persist an inbound message. Idempotent at the message level: replaying the
     * same (sender, receiver, interchange_ref, message_ref) after a successful process
     * raises a 409 rather than re-publishing events.
     */
    public EdifactMessage ingest(String messageType, String rawMessage) {
        ParsedEdifact parsed = parser.parse(messageType, rawMessage);
        String sender = parsed.sender();
        String receiver = parsed.receiver();
        String interchangeRef = parsed.interchangeRef();
        String messageRef = parsed.messageReference();

        if (interchangeRef != null && messageRef != null && !interchangeRef.isBlank() && !messageRef.isBlank()) {
            messages.findBySenderAndReceiverAndInterchangeRefAndMessageRef(
                            sender, receiver, interchangeRef, messageRef)
                    .filter(m -> m.getStatus() == EdifactStatus.PROCESSED)
                    .ifPresent(existing -> {
                        throw new ConflictException("Message already processed (messageId="
                                + existing.getId() + "); duplicate interchange ignored");
                    });
        }
        return persistAndProcess(parsed, rawMessage);
    }

    /** Read a previously ingested message (superset endpoint). */
    @Transactional(readOnly = true)
    public EdifactMessage findMessage(UUID messageId) {
        return messages.findById(messageId)
                .orElseThrow(() -> new NotFoundException("No EDIFACT message with id " + messageId));
    }

    /**
     * Persist the parsed message with its lifecycle and emit domain events. Wrapped in a
     * retry so a transient persist/publish failure is retried before falling to the
     * dead-letter recovery path.
     */
    @Retryable(
            retryFor = {RuntimeException.class},
            maxAttemptsExpression = "${smartportx.integration.edifact.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${smartportx.integration.edifact.retry-delay-ms:500}"))
    @Transactional
    public EdifactMessage persistAndProcess(ParsedEdifact parsed, String rawMessage) {
        EdifactMessage message = new EdifactMessage();
        message.setMessageType(parsed.messageType());
        message.setRawMessage(rawMessage);
        message.setSender(parsed.sender());
        message.setReceiver(parsed.receiver());
        message.setDirection("INBOUND");
        message.setInterchangeRef(parsed.interchangeRef());
        message.setMessageRef(parsed.messageReference());
        message.setCorrelationId(UUID.fromString(CorrelationContext.getOrCreate()));
        message.setParsedData(buildParsedData(parsed));
        message.setStatus(EdifactStatus.PARSED);
        messages.save(message);
        log.info("Parsed EDIFACT {} messageRef={} (id={})",
                parsed.messageType(), parsed.messageReference(), message.getId());

        publish(parsed);
        message.setStatus(EdifactStatus.PROCESSED);
        message.setProcessedAt(Instant.now());
        messages.save(message);
        audit.record(source, "edifact." + parsed.messageType().toLowerCase(Locale.ROOT), "SUCCESS",
                parsedData(parsed), Map.of("messageId", message.getId().toString()), null);
        return message;
    }

    /** Dead-letter recovery: mark the message ERROR and record the failure in the audit trail. */
    @Recover
    @Transactional
    public EdifactMessage recoverParse(Exception e, ParsedEdifact parsed, String rawMessage) {
        log.error("EDIFACT {} process failed after retries (messageRef={}): {}",
                parsed.messageType(), parsed.messageReference(), e.getMessage());
        EdifactMessage message = new EdifactMessage();
        message.setMessageType(parsed.messageType());
        message.setRawMessage(rawMessage);
        message.setSender(parsed.sender());
        message.setReceiver(parsed.receiver());
        message.setInterchangeRef(parsed.interchangeRef());
        message.setMessageRef(parsed.messageReference());
        message.setCorrelationId(UUID.fromString(CorrelationContext.getOrCreate()));
        message.setStatus(EdifactStatus.ERROR);
        message.setErrorMessage(e.getMessage());
        messages.save(message);
        audit.record(source, "edifact." + parsed.messageType().toLowerCase(Locale.ROOT), "ERROR",
                parsedData(parsed), Map.of("messageId", message.getId().toString()), e.getMessage());
        return message;
    }

    private void publish(ParsedEdifact parsed) {
        transport.publish(DomainEvent.of(IntegrationDomainEvents.EDIFACT_PARSED, source,
                new IntegrationDomainEvents.EdifactParsedPayload(
                        UUID.randomUUID(), parsed.messageType(), parsed.messageReference(),
                        CorrelationContext.getOrCreate(), List.of())));

        if ("COPRAR".equals(parsed.messageType())) {
            List<IntegrationDomainEvents.ContainerUnit> units = new ArrayList<>();
            for (EdifactContainer c : parsed.containers()) {
                units.add(new IntegrationDomainEvents.ContainerUnit(
                        UUID.randomUUID(), c.containerNumber(), c.isoType(), c.status(),
                        c.operator(), c.yardPosition()));
            }
            transport.publish(DomainEvent.of(IntegrationDomainEvents.DISCHARGE_ORDER_RECEIVED, source,
                    new IntegrationDomainEvents.DischargeOrderPayload(
                            UUID.randomUUID(), parsed.messageReference(), parsed.vesselName(),
                            parsed.vesselImo(), parsed.portOfCall(), parsed.terminal(),
                            parsed.carrierCode(), parsed.eta(), units, parsed.validationWarnings())));
        } else if ("COPARN".equals(parsed.messageType())) {
            for (EdifactContainer c : parsed.containers()) {
                transport.publish(DomainEvent.of(IntegrationDomainEvents.ARRIVAL_ANNOUNCED, source,
                        new IntegrationDomainEvents.ArrivalAnnouncementPayload(
                                UUID.randomUUID(), parsed.messageReference(), c.containerNumber(),
                                c.isoType(), parsed.portOfCall(), parsed.terminal(),
                                parsed.carrierCode(), c.customsStatus(), parsed.eta())));
            }
        }
    }

    private Map<String, Object> buildParsedData(ParsedEdifact parsed) {
        Map<String, Object> data = parsedData(parsed);
        data.put("eta", parsed.eta() == null ? null : parsed.eta().toString());
        return data;
    }

    private Map<String, Object> parsedData(ParsedEdifact parsed) {
        Map<String, Object> carrier = new LinkedHashMap<>();
        carrier.put("name", parsed.carrierName());
        carrier.put("code", parsed.carrierCode());
        carrier.put("country", parsed.carrierCountry());

        List<Map<String, Object>> containers = new ArrayList<>();
        for (EdifactContainer c : parsed.containers()) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("containerNumber", c.containerNumber());
            cm.put("isoType", c.isoType());
            cm.put("status", c.status());
            cm.put("operator", c.operator());
            cm.put("yardPosition", c.yardPosition());
            cm.put("customsStatus", c.customsStatus());
            containers.add(cm);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messageReference", parsed.messageReference());
        data.put("messageDate", parsed.messageDate());
        data.put("messageTime", parsed.messageTime());
        data.put("sender", parsed.sender());
        data.put("receiver", parsed.receiver());
        data.put("interchangeRef", parsed.interchangeRef());
        data.put("carrier", carrier);
        data.put("vessel", new LinkedHashMap<>(Map.of(
                "name", parsed.vesselName(),
                "imo", parsed.vesselImo())));
        data.put("portOfCall", parsed.portOfCall());
        data.put("terminal", parsed.terminal());
        data.put("containers", containers);
        data.put("validationWarnings", parsed.validationWarnings());
        return data;
    }
}
