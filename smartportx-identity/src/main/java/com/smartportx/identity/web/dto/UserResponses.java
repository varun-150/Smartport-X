package com.smartportx.identity.web.dto;

import com.smartportx.identity.security.RolePermissions;

import java.time.Instant;

public final class UserResponses {

    private UserResponses() {
    }

    public static UserResponse from(com.smartportx.identity.domain.User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRole().name(),
                deriveStatus(user).name(),
                RolePermissions.of(user.getRole()),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    public static Status deriveStatus(com.smartportx.identity.domain.User user) {
        if (!user.isEnabled()) {
            return Status.INACTIVE;
        }
        if (user.getFailedLoginAttempts() >= 15) {
            return Status.LOCKED;
        }
        if (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(Instant.now())) {
            return Status.LOCKED;
        }
        return Status.ACTIVE;
    }
}