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
@Table(name = "container_status_history", schema = "smartportx_container")
public class ContainerStatusHistory {

    @Id
    private UUID id;

    @Column(name = "container_id", nullable = false)
    private UUID containerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private ContainerStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 20, nullable = false)
    private ContainerStatus newStatus;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(length = 255)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected ContainerStatusHistory() {
    }

    public static ContainerStatusHistory create(UUID containerId, ContainerStatus previousStatus,
                                                ContainerStatus newStatus, UUID changedBy, String reason) {
        ContainerStatusHistory history = new ContainerStatusHistory();
        history.containerId = containerId;
        history.previousStatus = previousStatus;
        history.newStatus = newStatus;
        history.changedBy = changedBy;
        history.reason = reason;
        history.changedAt = Instant.now();
        return history;
    }

    @PrePersist
    void prePersist() {
        this.id = this.id == null ? UUID.randomUUID() : this.id;
        if (this.changedAt == null) {
            this.changedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getContainerId() {
        return containerId;
    }

    public ContainerStatus getPreviousStatus() {
        return previousStatus;
    }

    public ContainerStatus getNewStatus() {
        return newStatus;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public String getReason() {
        return reason;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}