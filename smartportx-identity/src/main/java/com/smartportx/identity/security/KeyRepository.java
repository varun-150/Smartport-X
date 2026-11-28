package com.smartportx.identity.security;

import com.smartportx.common.security.AccessTokenValidators;
import com.smartportx.identity.config.SecurityProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

@Component
public class KeyRepository {

    private final RSAKey rsaKey;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public KeyRepository(SecurityProperties properties, Environment environment) {
        try {
            KeyPair keyPair = loadConfiguredKeyPair(environment);
            if (keyPair == null) {
                if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
                    throw new IllegalStateException(
                            "Production requires JWT_KEYSTORE_LOCATION, JWT_KEYSTORE_PASSWORD, and JWT_KEY_ALIAS");
                }
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(properties.jwt().keySize());
                keyPair = generator.generateKeyPair();
            }

            this.rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey(keyPair.getPrivate())
                    .keyID("identity-1")
                    .build();
            this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet(new JWKSet(rsaKey)));
            NimbusJwtDecoder identityDecoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
            identityDecoder.setJwtValidator(AccessTokenValidators.forIssuer(properties.jwt().issuer()));
            this.decoder = identityDecoder;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialise RSA key material", e);
        }
    }

    private KeyPair loadConfiguredKeyPair(Environment environment) throws Exception {
        String location = environment.getProperty("JWT_KEYSTORE_LOCATION", "").trim();
        String password = environment.getProperty("JWT_KEYSTORE_PASSWORD", "");
        String alias = environment.getProperty("JWT_KEY_ALIAS", "").trim();

        boolean anyConfigured = !location.isEmpty() || !password.isEmpty() || !alias.isEmpty();
        if (!anyConfigured) {
            return null;
        }
        if (location.isEmpty() || password.isEmpty() || alias.isEmpty()) {
            throw new IllegalStateException(
                    "JWT keystore configuration is incomplete; set location, password, and alias together");
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(Path.of(location))) {
            keyStore.load(input, password.toCharArray());
        }
        if (!keyStore.isKeyEntry(alias)) {
            throw new IllegalStateException("Configured JWT keystore alias is not a private-key entry");
        }
        java.security.Key key = keyStore.getKey(alias, password.toCharArray());
        java.security.cert.Certificate certificate = keyStore.getCertificate(alias);
        if (!(key instanceof RSAPrivateKey privateKey)
                || certificate == null
                || !(certificate.getPublicKey() instanceof RSAPublicKey publicKey)) {
            throw new IllegalStateException("Configured JWT keystore entry must contain an RSA private key and certificate");
        }
        return new KeyPair(publicKey, privateKey);
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
