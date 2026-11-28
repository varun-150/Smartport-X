package com.smartportx.identity.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "username is required")
        @Size(min = 3, max = 50)
        @Pattern(regexp = "[A-Za-z0-9_]+", message = "username may contain letters, digits and underscore only")
        String username,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 128)
        String password) {
}