package com.smartportx.gate.domain;

/**
 * Appointment direction from the terminal's perspective. Value set per API-CONTRACTS &sect;6.1.
 */
public enum AppointmentType {
    PICKUP,
    DROP_OFF,
    BOTH;

    /**
     * The quota bucket a booking reserves against. A BOTH appointment occupies the outbound
     * (return) leg quota. See PHASE-4-DECISIONS D-446.
     */
    public AppointmentType quotaType() {
        return this == PICKUP ? PICKUP : DROP_OFF;
    }
}