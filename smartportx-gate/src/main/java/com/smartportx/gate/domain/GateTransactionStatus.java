package com.smartportx.gate.domain;

/**
 * Lifecycle of a single gate transaction (one visit = one row, gate in then gate out).
 */
public enum GateTransactionStatus {
    CHECKED_IN,
    CHECKED_OUT
}