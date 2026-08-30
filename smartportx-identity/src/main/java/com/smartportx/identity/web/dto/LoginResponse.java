package com.smartportx.identity.web.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType,
        UserResponse user) {
}