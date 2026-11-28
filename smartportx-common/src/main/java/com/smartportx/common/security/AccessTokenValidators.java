package com.smartportx.common.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

/**
 * Shared JWT validation policy for HTTP resource servers.
 *
 * <p>All service decoders must enforce the expected issuer, standard timestamp
 * validation, and reject refresh JWTs when an access token is required.</p>
 */
public final class AccessTokenValidators {

    private static final OAuth2Error REFRESH_TOKEN_ERROR = new OAuth2Error(
            "invalid_token", "Refresh tokens are not valid access tokens", null);

    private AccessTokenValidators() {
    }

    public static OAuth2TokenValidator<Jwt> forIssuer(String issuer) {
        OAuth2TokenValidator<Jwt> accessTokenOnly = jwt ->
                "refresh".equals(jwt.getClaimAsString("type"))
                        ? OAuth2TokenValidatorResult.failure(REFRESH_TOKEN_ERROR)
                        : OAuth2TokenValidatorResult.success();

        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                accessTokenOnly);
    }
}
