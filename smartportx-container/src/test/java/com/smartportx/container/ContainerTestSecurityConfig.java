package com.smartportx.container;

import com.nimbusds.jose.JOSEException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@TestConfiguration
public class ContainerTestSecurityConfig {

    @Bean
    public JwtDecoder jwtDecoder() throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TestTokens.publicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(TestTokens.ISSUER));
        return decoder;
    }
}