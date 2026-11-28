package com.smartportx.container.web.dto;

import com.smartportx.container.domain.MovementType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record RegisterMovementRequest(
        @NotNull MovementType movementType,
        @Size(max = 50) String from,
        @Size(max = 50) String to,
        @NotNull Instant timestamp,
        @Size(max = 255) String reason) {
}