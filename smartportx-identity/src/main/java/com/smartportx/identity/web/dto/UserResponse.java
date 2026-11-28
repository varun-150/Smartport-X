package com.smartportx.identity.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String displayName,
        String email,
        String role,
        String status,
        List<String> permissions,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {
}