package com.smartportx.gate.domain;

/**
 * Appointment lifecycle. Value set per API-CONTRACTS &sect;6.1.
 */
public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    CHECKED_IN,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    public boolean isActive() {
        return this == CONFIRMED || this == CHECKED_IN || this == IN_PROGRESS;
    }

    public boolean isModifiable() {
        return this == PENDING || this == CONFIRMED;
    }
}