package com.smartportx.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "smartportx.security")
public record SecurityProperties(
        Jwt jwt,
        int bcryptStrength,
        int loginRateLimit,
        boolean seedDemoUsers) {

    public SecurityProperties {
        if (bcryptStrength <= 0) {
            bcryptStrength = 12;
        }
        if (loginRateLimit <= 0) {
            loginRateLimit = 5;
        }
        if (jwt == null) {
            jwt = new Jwt("smartportx-identity", Duration.ofMinutes(15), Duration.ofDays(7), 2048);
        }
    }

    public record Jwt(String issuer, Duration accessTokenTtl, Duration refreshTokenTtl, int keySize) {
        public Jwt {
            if (keySize <= 0) {
                keySize = 2048;
            }
            if (issuer == null || issuer.isBlank()) {
                issuer = "smartportx-identity";
            }
            if (accessTokenTtl == null) {
                accessTokenTtl = Duration.ofMinutes(15);
            }
            if (refreshTokenTtl == null) {
                refreshTokenTtl = Duration.ofDays(7);
            }
        }
    }
}