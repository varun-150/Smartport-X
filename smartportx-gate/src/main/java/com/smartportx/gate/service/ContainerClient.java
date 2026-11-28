package com.smartportx.gate.service;

import java.util.Optional;

/**
 * Read-side view of the container service (smartportx-container, M4).
 * Fail-secure contract: an unreadable provider returns {@link Optional#empty()},
 * which callers treat as "container cannot be confirmed" and block.
 */
public interface ContainerClient {

    Optional<ContainerInfo> findByContainerNumber(String containerNumber);

    record ContainerInfo(String containerNumber, String containerType, Double weightKg,
                         boolean dangerousGoods, String status) {
    }
}