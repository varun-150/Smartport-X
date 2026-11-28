package com.smartportx.yard.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ContainerCatalog {

    Optional<ContainerInfo> findById(UUID containerId);

    record ContainerInfo(UUID id, String containerNumber, String containerType, Double weightKg,
                         boolean dangerousGoods, String status, Instant receivedAt,
                         Instant estimatedDeparture) {
    }
}