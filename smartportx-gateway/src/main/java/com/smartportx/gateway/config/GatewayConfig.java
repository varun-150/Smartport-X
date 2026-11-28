package com.smartportx.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@Configuration
@EnableConfigurationProperties(SmartPortxGatewayProperties.class)
public class GatewayConfig {

    /**
     * Edges use the identity service's JWKS endpoint for RS256 verification.
     * Tests override this bean with a @Primary decoder bound to a local RSA key.
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${IDENTITY_JWKS_URI:http://localhost:8081/api/v1/auth/jwks}") String jwkSetUri,
            @Value("${SMARTPORTX_JWT_ISSUER:smartportx-identity}") String issuer) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
}