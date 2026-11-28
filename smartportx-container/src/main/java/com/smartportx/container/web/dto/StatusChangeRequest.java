package com.smartportx.container.web.dto;

import com.smartportx.container.domain.ContainerStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusChangeRequest(
        @NotNull ContainerStatus status,
        @Size(max = 50) String location,
        @Size(max = 255) String reason,
        @Size(max = 100) String operator) {
}