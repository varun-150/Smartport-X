package com.smartportx.identity.web.dto;

public record RefreshResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType) {
}