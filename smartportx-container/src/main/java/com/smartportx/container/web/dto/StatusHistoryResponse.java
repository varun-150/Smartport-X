package com.smartportx.container.web.dto;

import com.smartportx.container.domain.ContainerStatus;
import com.smartportx.container.domain.ContainerStatusHistory;

import java.time.Instant;
import java.util.UUID;

public record StatusHistoryResponse(
        UUID id,
        UUID containerId,
        String containerNumber,
        ContainerStatus previousStatus,
        ContainerStatus newStatus,
        UUID changedBy,
        String reason,
        Instant changedAt) {

    public static StatusHistoryResponse from(String containerNumber, ContainerStatusHistory history) {
        return new StatusHistoryResponse(
                history.getId(), history.getContainerId(), containerNumber,
                history.getPreviousStatus(), history.getNewStatus(), history.getChangedBy(),
                history.getReason(), history.getChangedAt());
    }
}