package com.smartportx.gate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable snapshot of gate queue state taken after each check-in/check-out.
 * DATA-ARCHITECTURE &sect;8.5.
 */
@Entity
@Table(name = "queue_status", schema = "smartportx_gate")
public class QueueStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "snapshot_time", nullable = false)
    private Instant snapshotTime;

    @Column(name = "waiting_trucks", nullable = false)
    private int waitingTrucks;

    @Column(name = "pickup_count", nullable = false)
    private int pickupCount;

    @Column(name = "dropoff_count", nullable = false)
    private int dropoffCount;

    @Column(name = "avg_wait_time_minutes")
    private Double avgWaitTimeMinutes;

    @Column(name = "estimated_clearance")
    private Instant estimatedClearance;

    @PrePersist
    void prePersist() {
        if (snapshotTime == null) {
            snapshotTime = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Instant getSnapshotTime() {
        return snapshotTime;
    }

    public void setSnapshotTime(Instant snapshotTime) {
        this.snapshotTime = snapshotTime;
    }

    public int getWaitingTrucks() {
        return waitingTrucks;
    }

    public void setWaitingTrucks(int waitingTrucks) {
        this.waitingTrucks = waitingTrucks;
    }

    public int getPickupCount() {
        return pickupCount;
    }

    public void setPickupCount(int pickupCount) {
        this.pickupCount = pickupCount;
    }

    public int getDropoffCount() {
        return dropoffCount;
    }

    public void setDropoffCount(int dropoffCount) {
        this.dropoffCount = dropoffCount;
    }

    public Double getAvgWaitTimeMinutes() {
        return avgWaitTimeMinutes;
    }

    public void setAvgWaitTimeMinutes(Double avgWaitTimeMinutes) {
        this.avgWaitTimeMinutes = avgWaitTimeMinutes;
    }

    public Instant getEstimatedClearance() {
        return estimatedClearance;
    }

    public void setEstimatedClearance(Instant estimatedClearance) {
        this.estimatedClearance = estimatedClearance;
    }
}