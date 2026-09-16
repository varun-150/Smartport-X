package com.smartportx.gate.service;

import com.smartportx.gate.domain.AppointmentType;
import com.smartportx.gate.domain.GateType;

import java.util.List;
import java.util.Set;

/**
 * Gate business rules that are shared across services. See PHASE-4-DECISIONS D-446.
 */
public final class GateRules {

    public static final List<String> PICKUP_DOCUMENTS = List.of("BILL_OF_LADING", "DELIVERY_ORDER");
    public static final List<String> DROP_OFF_DOCUMENTS = List.of("DELIVERY_ORDER");

    /**
     * Container service states a PICKUP truck may haul out of the terminal
     * (container statuses per DATA-ARCHITECTURE &sect;7.1).
     */
    public static final Set<String> PICKUPABLE_CONTAINER_STATES = Set.of(
            "REGISTERED", "AT_GATE", "IN_YARD", "DISCHARGED", "AT_GATE_OUT");

    private GateRules() {
    }

    /** Document ids that must be submitted for the appointment type. */
    public static List<String> requiredDocuments(AppointmentType type) {
        if (type == AppointmentType.DROP_OFF) {
            return DROP_OFF_DOCUMENTS;
        }
        return PICKUP_DOCUMENTS; // PICKUP and BOTH require DO + B/L
    }

    /** Gate crossing direction for a transaction of this appointment type. */
    public static GateType gateTypeFor(AppointmentType type) {
        return type == AppointmentType.DROP_OFF ? GateType.INBOUND : GateType.OUTBOUND;
    }
}