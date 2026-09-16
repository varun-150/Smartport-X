package com.smartportx.gateway;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * Overrides the production JWKS-based {@code ReactiveJwtDecoder} with a local public key
 * decoder so edge tests need no running identity service.
 */
@TestConfiguration
public class GatewayTestSecurityConfig {

    @Bean
    @Primary
    public ReactiveJwtDecoder jwtDecoder() {
        NimbusReactiveJwtDecoder decoder =
                NimbusReactiveJwtDecoder.withPublicKey(GatewayTestTokens.publicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(GatewayTestTokens.ISSUER));
        return decoder;
    }
}