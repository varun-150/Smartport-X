package com.smartportx.integration.domain;

/**
 * Lifecycle of an inbound EDIFACT message. DATA-ARCHITECTURE &sect;9.1/10.1.
 * RECEIVED on ingest, PARSED once tokenised+validated, PROCESSED once mapped to a
 * domain event, ERROR when validation or mapping fails.
 */
public enum EdifactStatus {
    RECEIVED,
    PARSED,
    PROCESSED,
    ERROR
}
