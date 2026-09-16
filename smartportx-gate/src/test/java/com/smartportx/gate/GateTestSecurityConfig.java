package com.smartportx.gate;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@TestConfiguration
public class GateTestSecurityConfig {

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(GateTestTokens.publicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(GateTestTokens.ISSUER));
        return decoder;
    }
}