package com.smartportx.identity.security;

import com.smartportx.identity.config.SecurityProperties;
import com.smartportx.identity.domain.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final SecurityProperties properties;
    private final KeyRepository keys;

    public JwtTokenService(SecurityProperties properties, KeyRepository keys) {
        this.properties = properties;
        this.keys = keys;
    }

    public String accessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.jwt().accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("roles", java.util.List.of(user.getRole().name()))
                .claim("permissions", RolePermissions.of(user.getRole()))
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .build();
        return encode(claims, keys.encoder());
    }

    public TokenAndExpiry refreshToken(User user, UUID familyId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.jwt().refreshTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiry)
                .id(UUID.randomUUID().toString())
                .claim("type", "refresh")
                .claim("fam", familyId.toString())
                .claim("username", user.getUsername())
                .build();
        return new TokenAndExpiry(encode(claims, keys.encoder()), expiry);
    }

    public Jwt decode(String token) {
        return keys.decoder().decode(token);
    }

    public String jwks() {
        return keys.jwks();
    }

    public long accessTokenTtlSeconds() {
        return properties.jwt().accessTokenTtl().toSeconds();
    }

    private String encode(JwtClaimsSet claims, JwtEncoder encoder) {
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public record TokenAndExpiry(String token, Instant expiresAt) {
    }
}