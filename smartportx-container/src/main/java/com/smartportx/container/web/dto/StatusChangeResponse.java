package com.smartportx.container.web.dto;

import com.smartportx.container.domain.ContainerStatus;

import java.time.Instant;
import java.util.UUID;

public record StatusChangeResponse(
        UUID containerId,
        String containerNumber,
        ContainerStatus previousStatus,
        ContainerStatus newStatus,
        Instant transitionTime,
        String location) {
}