package com.smartportx.integration.service;

import com.smartportx.common.error.ConflictException;
import com.smartportx.common.error.NotFoundException;
import com.smartportx.common.event.DomainEvent;
import com.smartportx.integration.domain.CustomsDecision;
import com.smartportx.integration.domain.CustomsState;
import com.smartportx.integration.domain.CustomsStatus;
import com.smartportx.integration.domain.events.IntegrationDomainEvents;
import com.smartportx.integration.event.EventTransport;
import com.smartportx.integration.repository.CustomsStatusRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Simulated customs clearance (FR-013). Each container follows
 * PENDING -> UNDER_REVIEW -> CLEARED | HELD -> RELEASED. The gate-out flow consults
 * {@link #validateGateOut} so a non-cleared or held container cannot leave the terminal.
 */
@Service
public class CustomsService {

    public record GateOutResult(String containerNumber, String status, boolean allowed, String reason) {
    }

    private final CustomsStatusRepository repository;
    private final EventTransport transport;
    private final IntegrationAuditService audit;
    private final String source;

    public CustomsService(CustomsStatusRepository repository,
                          EventTransport transport,
                          IntegrationAuditService audit,
                          @Value("${smartportx.integration.source:smartportx-integration}") String source) {
        this.repository = repository;
        this.transport = transport;
        this.audit = audit;
        this.source = source;
    }

    @Transactional
    public CustomsStatus submit(String containerNumber, String declarationNumber, String reviewedBy) {
        requireContainerNumber(containerNumber);
        CustomsStatus existing = repository.findByContainerNumber(containerNumber).orElse(null);
        if (existing != null && existing.getStatus() != CustomsState.PENDING) {
            throw new ConflictException("Customs record for " + containerNumber
                    + " already under review or decided (status=" + existing.getStatus() + ")");
        }
        CustomsStatus record = existing == null ? new CustomsStatus() : existing;
        if (record.getId() == null) {
            record.setContainerNumber(containerNumber);
        }
        record.setDeclarationNumber(declarationNumber);
        record.setReviewedBy(reviewedBy);
        record.setStatus(CustomsState.UNDER_REVIEW);
        CustomsStatus saved = repository.save(record);
        audit.record(source, "customs.submit", "SUCCESS",
                Map.of("containerNumber", containerNumber), Map.of("status", saved.getStatus().name()), null);
        return saved;
    }

    @Transactional
    public CustomsStatus review(String containerNumber, CustomsDecision decision, String reason) {
        requireContainerNumber(containerNumber);
        CustomsStatus record = repository.findByContainerNumber(containerNumber)
                .orElseThrow(() -> new NotFoundException("No customs record for container " + containerNumber));
        CustomsState previous = record.getStatus();
        if (decision == CustomsDecision.CLEARED) {
            record.setStatus(CustomsState.CLEARED);
            record.setClearedAt(Instant.now());
            record.setClearedBy(record.getReviewedBy());
            record.setReason(null);
        } else {
            record.setStatus(CustomsState.HELD);
            record.setReason(reason == null ? "Held by customs" : reason);
        }
        CustomsStatus saved = repository.save(record);
        publish(previous.name(), saved);
        audit.record(source, "customs.review", "SUCCESS",
                Map.of("containerNumber", containerNumber, "decision", decision.name()),
                Map.of("status", saved.getStatus().name()), null);
        return saved;
    }

    @Transactional
    public CustomsStatus release(String containerNumber) {
        requireContainerNumber(containerNumber);
        CustomsStatus record = repository.findByContainerNumber(containerNumber)
                .orElseThrow(() -> new NotFoundException("No customs record for container " + containerNumber));
        if (record.getStatus() != CustomsState.CLEARED) {
            throw new ConflictException("Container " + containerNumber
                    + " is not cleared and cannot be released (status=" + record.getStatus() + ")");
        }
        CustomsState previous = record.getStatus();
        record.setStatus(CustomsState.RELEASED);
        CustomsStatus saved = repository.save(record);
        publish(previous.name(), saved);
        audit.record(source, "customs.release", "SUCCESS",
                Map.of("containerNumber", containerNumber), Map.of("status", saved.getStatus().name()), null);
        return saved;
    }

    @Transactional(readOnly = true)
    public CustomsStatus getOrThrow(String containerNumber) {
        requireContainerNumber(containerNumber);
        return repository.findByContainerNumber(containerNumber)
                .orElseThrow(() -> new NotFoundException("No customs record for container " + containerNumber));
    }

    /**
     * Gate-out check (FR-013): a container may leave only when its customs status is
     * {@code RELEASED} or {@code CLEARED}; {@code HELD} blocks gate-out.
     */
    @Transactional(readOnly = true)
    public GateOutResult validateGateOut(String containerNumber) {
        requireContainerNumber(containerNumber);
        CustomsStatus record = repository.findByContainerNumber(containerNumber)
                .orElseThrow(() -> new NotFoundException("No customs record for container " + containerNumber));
        boolean allowed = record.getStatus() == CustomsState.RELEASED || record.getStatus() == CustomsState.CLEARED;
        String reason = allowed ? null
                : ("Container " + containerNumber + " is " + record.getStatus() + "; gate-out blocked");
        return new GateOutResult(containerNumber, record.getStatus().name(), allowed, reason);
    }

    private void publish(String previous, CustomsStatus record) {
        transport.publish(DomainEvent.of(IntegrationDomainEvents.CUSTOMS_STATUS_CHANGED, source,
                new IntegrationDomainEvents.CustomsChangedPayload(
                        record.getContainerNumber(), previous, record.getStatus().name(),
                        record.getReason(), Instant.now())));
    }

    private void requireContainerNumber(String containerNumber) {
        if (containerNumber == null || containerNumber.isBlank()) {
            throw new NotFoundException("containerNumber is required");
        }
    }
}
