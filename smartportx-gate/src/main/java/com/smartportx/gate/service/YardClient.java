package com.smartportx.gate.service;

import java.util.Optional;

/**
 * Read-side view of the yard service (smartportx-yard, M5).
 * Fail-secure: {@link Optional#empty()} means the yard assignment could not be
 * confirmed and callers must block the gate crossing.
 */
public interface YardClient {

    /**
     * @return true when the container is currently assigned a yard position;
     *         false when released/no assignment; empty when the yard is unreachable.
     */
    Optional<Boolean> isPositionAssigned(String containerNumber);
}