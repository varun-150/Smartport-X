package com.smartportx.integration.domain;

/**
 * Customs clearance lifecycle used by the simulated customs service (FR-013).
 * PENDING -> UNDER_REVIEW -> CLEARED | HELD -> RELEASED.
 */
public enum CustomsState {
    PENDING,
    UNDER_REVIEW,
    CLEARED,
    HELD,
    RELEASED
}
