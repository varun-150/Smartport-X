package com.smartportx.container.web.dto;

import com.smartportx.container.domain.Container;
import com.smartportx.container.domain.ContainerMovement;

import java.time.Instant;
import java.util.UUID;

public record MovementResponse(
        UUID movementId,
        UUID containerId,
        String containerNumber,
        String movementType,
        String from,
        String to,
        Instant timestamp,
        String operator,
        String reason,
        Instant createdAt) {

    public static MovementResponse from(Container container, ContainerMovement movement) {
        return new MovementResponse(
                movement.getId(), movement.getContainerId(), container.getContainerNumber(),
                movement.getMovementType().name(), movement.getFromLocation(), movement.getToLocation(),
                movement.getTimestamp(), movement.getPerformedBy(), movement.getReason(),
                movement.getCreatedAt());
    }
}