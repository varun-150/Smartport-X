package com.smartportx.gate.domain;

/**
 * Direction of the gate crossing. INBOUND = truck enters with a container (drop-off),
 * OUTBOUND = truck leaves with a container (pickup). See PHASE-4-DECISIONS D-446.
 */
public enum GateType {
    INBOUND,
    OUTBOUND
}