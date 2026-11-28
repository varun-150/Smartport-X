package com.smartportx.identity.web.dto;

import com.smartportx.identity.domain.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "username is required")
        @Size(min = 3, max = 50)
        @Pattern(regexp = "[A-Za-z0-9_]+", message = "username may contain letters, digits and underscore only")
        String username,

        @NotBlank(message = "email is required")
        @Email
        @Size(max = 100)
        String email,

        @NotBlank(message = "displayName is required")
        @Size(min = 2, max = 100)
        String displayName,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 128)
        @Pattern(regexp = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).+$",
                message = "password must contain at least one uppercase letter, one digit and one special character")
        String password,

        @NotNull(message = "role is required")
        RoleName role,

        Status status) {

    public Status effectiveStatus() {
        return status == null ? Status.ACTIVE : status;
    }
}