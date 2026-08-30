package com.smartportx.identity.security;

import com.smartportx.identity.config.SecurityProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;

@Component
public class KeyRepository {

    private final RSAKey rsaKey;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public KeyRepository(SecurityProperties properties) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(properties.jwt().keySize());
            KeyPair keyPair = generator.generateKeyPair();
            this.rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey(keyPair.getPrivate())
                    .keyID("identity-1")
                    .build();
            this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet(new JWKSet(rsaKey)));
            this.decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialise RSA key material", e);
        }
    }

    public JwtEncoder encoder() {
        return encoder;
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    public String jwks() {
        return new JWKSet(rsaKey.toPublicJWK()).toString(false);
    }
}