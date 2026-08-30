package com.smartportx.container.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartportx.common.correlation.CorrelationContext;
import com.smartportx.common.event.DomainEvent;
import com.smartportx.container.domain.Container;
import com.smartportx.container.domain.ContainerEvent;
import com.smartportx.container.domain.ContainerMovement;
import com.smartportx.container.domain.ContainerStatus;
import com.smartportx.container.domain.events.ContainerDomainEvents;
import com.smartportx.container.event.EventTransport;
import com.smartportx.container.repository.ContainerEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
public class ContainerEventLogger {

    private final ContainerEventRepository events;
    private final EventTransport transport;
    private final ObjectMapper objectMapper;
    private final String source;

    public ContainerEventLogger(ContainerEventRepository events, EventTransport transport,
                                ObjectMapper objectMapper,
                                @Value("${smartportx.container.source}") String source) {
        this.events = events;
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.source = source;
    }

    public void registered(Container container) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("containerId", container.getId().toString());
        data.put("containerNumber", container.getContainerNumber());
        data.put("containerType", container.getContainerType().value());
        data.put("status", container.getStatus().name());
        data.put("createdAt", container.getCreatedAt().toString());
        appendAndPublish(container.getId(), ContainerDomainEvents.LOG_REGISTERED,
                ContainerDomainEvents.REGISTERED, data);
    }

    public void statusChanged(Container container, ContainerStatus previousStatus, String location) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("containerId", container.getId().toString());
        data.put("containerNumber", container.getContainerNumber());
        data.put("previousStatus", previousStatus.name());
        data.put("newStatus", container.getStatus().name());
        if (location != null) {
            data.put("location", location);
        }
        data.put("changedAt", container.getUpdatedAt().toString());
        appendAndPublish(container.getId(), ContainerDomainEvents.LOG_STATUS_CHANGE,
                ContainerDomainEvents.STATUS_CHANGED, data);
    }

    public void moved(Container container, ContainerMovement movement) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("movementId", movement.getId().toString());
        data.put("containerId", container.getId().toString());
        data.put("containerNumber", container.getContainerNumber());
        data.put("movementType", movement.getMovementType().name());
        if (movement.getFromLocation() != null) {
            data.put("from", movement.getFromLocation());
        }
        if (movement.getToLocation() != null) {
            data.put("to", movement.getToLocation());
        }
        data.put("timestamp", movement.getTimestamp().toString());
        data.put("performedBy", movement.getPerformedBy());
        appendAndPublish(container.getId(), ContainerDomainEvents.LOG_MOVEMENT,
                ContainerDomainEvents.MOVED, data);
    }

    public void removed(Container container) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("containerId", container.getId().toString());
        data.put("containerNumber", container.getContainerNumber());
        data.put("removedAt", container.getRemovedAt().toString());
        appendAndPublish(container.getId(), ContainerDomainEvents.LOG_REMOVED,
                ContainerDomainEvents.REMOVED, data);
    }

    private void appendAndPublish(UUID containerId, String logToken, String envelopeType, JsonNode data) {
        Instant occurred = Instant.now();
        UUID correlationId = correlationId();
        events.save(ContainerEvent.create(containerId, logToken, data, correlationId, occurred));
        transport.publish(new DomainEvent<>(UUID.randomUUID(), envelopeType, occurred, source,
                CorrelationContext.getOrCreate(), data));
    }

    private UUID correlationId() {
        return CorrelationContext.current()
                .map(ContainerEventLogger::toUuid)
                .orElseGet(UUID::randomUUID);
    }

    private static UUID toUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8));
        }
    }
}