package com.smartportx.identity.web.dto;

import com.smartportx.identity.domain.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Email
        @Size(max = 100)
        String email,

        @Size(min = 2, max = 100)
        String displayName,

        RoleName role,

        Status status) {
}