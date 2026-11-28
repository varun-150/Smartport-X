package com.smartportx.container.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "container_events", schema = "smartportx_container")
public class ContainerEvent {

    @Id
    private UUID id;

    @Column(name = "container_id", nullable = false)
    private UUID containerId;

    @Column(name = "event_type", length = 30, nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_data", nullable = false)
    private JsonNode eventData;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ContainerEvent() {
    }

    public static ContainerEvent create(UUID containerId, String eventType, JsonNode eventData,
                                        UUID correlationId, Instant timestamp) {
        ContainerEvent event = new ContainerEvent();
        event.containerId = containerId;
        event.eventType = eventType;
        event.eventData = eventData;
        event.correlationId = correlationId;
        event.timestamp = timestamp;
        return event;
    }

    @PrePersist
    void prePersist() {
        this.id = this.id == null ? UUID.randomUUID() : this.id;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getContainerId() {
        return containerId;
    }

    public String getEventType() {
        return eventType;
    }

    public JsonNode getEventData() {
        return eventData;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}