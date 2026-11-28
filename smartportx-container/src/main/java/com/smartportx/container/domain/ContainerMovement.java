package com.smartportx.container.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "container_movements", schema = "smartportx_container")
public class ContainerMovement {

    @Id
    private UUID id;

    @Column(name = "container_id", nullable = false)
    private UUID containerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", length = 30, nullable = false)
    private MovementType movementType;

    @Column(name = "from_location", length = 50)
    private String fromLocation;

    @Column(name = "to_location", length = 50)
    private String toLocation;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "performed_by", length = 100, nullable = false)
    private String performedBy;

    @Column(length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ContainerMovement() {
    }

    public static ContainerMovement create(UUID containerId, MovementType movementType, String fromLocation,
                                           String toLocation, Instant timestamp, String performedBy, String reason) {
        ContainerMovement movement = new ContainerMovement();
        movement.containerId = containerId;
        movement.movementType = movementType;
        movement.fromLocation = fromLocation;
        movement.toLocation = toLocation;
        movement.timestamp = timestamp;
        movement.performedBy = performedBy;
        movement.reason = reason;
        return movement;
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

    public MovementType getMovementType() {
        return movementType;
    }

    public String getFromLocation() {
        return fromLocation;
    }

    public String getToLocation() {
        return toLocation;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}