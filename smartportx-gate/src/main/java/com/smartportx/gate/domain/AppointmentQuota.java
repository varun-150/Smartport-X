package com.smartportx.gate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-time-window booking quota, used to accept or reject appointment requests
 * (dynamic capacity management). DATA-ARCHITECTURE &sect;8.4.
 */
@Entity
@Table(name = "appointment_quotas", schema = "smartportx_gate")
public class AppointmentQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "time_window_start", nullable = false)
    private Instant timeWindowStart;

    @Column(name = "time_window_end", nullable = false)
    private Instant timeWindowEnd;

    @Column(name = "max_appointments", nullable = false)
    private int maxAppointments;

    @Column(name = "current_bookings", nullable = false)
    private int currentBookings;

    @Enumerated(EnumType.STRING)
    @Column(name = "appointment_type", nullable = false, length = 10)
    private AppointmentType appointmentType;

    @Column(name = "gate_lane", nullable = false, length = 20)
    private String gateLane = "ALL";

    @Column(name = "dynamic_adjustment", nullable = false)
    private boolean dynamicAdjustment;

    @Column(name = "adjusted_by")
    private UUID adjustedBy;

    @Column(name = "adjustment_reason", length = 200)
    private String adjustmentReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Instant getTimeWindowStart() {
        return timeWindowStart;
    }

    public void setTimeWindowStart(Instant timeWindowStart) {
        this.timeWindowStart = timeWindowStart;
    }

    public Instant getTimeWindowEnd() {
        return timeWindowEnd;
    }

    public void setTimeWindowEnd(Instant timeWindowEnd) {
        this.timeWindowEnd = timeWindowEnd;
    }

    public int getMaxAppointments() {
        return maxAppointments;
    }

    public void setMaxAppointments(int maxAppointments) {
        this.maxAppointments = maxAppointments;
    }

    public int getCurrentBookings() {
        return currentBookings;
    }

    public void setCurrentBookings(int currentBookings) {
        this.currentBookings = currentBookings;
    }

    public AppointmentType getAppointmentType() {
        return appointmentType;
    }

    public void setAppointmentType(AppointmentType appointmentType) {
        this.appointmentType = appointmentType;
    }

    public String getGateLane() {
        return gateLane;
    }

    public void setGateLane(String gateLane) {
        this.gateLane = gateLane;
    }

    public boolean isDynamicAdjustment() {
        return dynamicAdjustment;
    }

    public void setDynamicAdjustment(boolean dynamicAdjustment) {
        this.dynamicAdjustment = dynamicAdjustment;
    }

    public UUID getAdjustedBy() {
        return adjustedBy;
    }

    public void setAdjustedBy(UUID adjustedBy) {
        this.adjustedBy = adjustedBy;
    }

    public String getAdjustmentReason() {
        return adjustmentReason;
    }

    public void setAdjustmentReason(String adjustmentReason) {
        this.adjustmentReason = adjustmentReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public int availableSlots() {
        return maxAppointments - currentBookings;
    }
}