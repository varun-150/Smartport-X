package com.smartportx.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenValidatorsTest {

    private static final String ISSUER = "smartportx-identity";

    @Test
    void acceptsAccessJwtFromConfiguredIssuer() {
        var result = AccessTokenValidators.forIssuer(ISSUER).validate(jwt(ISSUER, null));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsRefreshJwtAsAccessCredential() {
        var result = AccessTokenValidators.forIssuer(ISSUER).validate(jwt(ISSUER, "refresh"));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anySatisfy(error ->
                assertThat(error.getDescription()).contains("Refresh tokens"));
    }

    @Test
    void rejectsJwtFromUnexpectedIssuer() {
        var result = AccessTokenValidators.forIssuer(ISSUER).validate(jwt("untrusted-issuer", null));

        assertThat(result.hasErrors()).isTrue();
    }

    private static Jwt jwt(String issuer, String type) {
        Instant now = Instant.now();
        var builder = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuer(issuer)
                .subject("test-subject")
                .issuedAt(now.minusSeconds(1))
                .expiresAt(now.plusSeconds(60))
                .claim("roles", List.of("ADMIN"));
        if (type != null) {
            builder.claim("type", type);
        }
        return builder.build();
    }
}
