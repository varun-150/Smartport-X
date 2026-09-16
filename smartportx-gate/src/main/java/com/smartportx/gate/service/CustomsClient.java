package com.smartportx.gate.service;

import java.util.Optional;

/**
 * Read-side view of the integration/customs service (smartportx-integration, M8).
 * HELD blocks gate crossings. Fail-secure: {@link Optional#empty()} means the
 * status could not be confirmed and callers must block the gate-out.
 */
public interface CustomsClient {

    Optional<CustomsControlStatus> statusFor(String containerNumber);

    record CustomsControlStatus(String status) {
        public boolean isHeld() {
            return status != null && "HELD".equalsIgnoreCase(status);
        }

        public boolean isCleared() {
            return status != null && "CLEARED".equalsIgnoreCase(status);
        }
    }
}