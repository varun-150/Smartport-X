package com.smartportx.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * RS256 tokens for gateway edge tests. A fixed {@link #FIXED_SUBJECT} is used by the rate
 * limit test so multiple requests share one token-bucket key.
 */
public final class GatewayTestTokens {

    public static final String ISSUER = "smartportx-identity";
    public static final String FIXED_SUBJECT = "11111111-1111-1111-1111-111111111111";

    private static final RSAKey RSA_KEY = generateKey();

    private GatewayTestTokens() {
    }

    public static RSAPublicKey publicKey() {
        try {
            return RSA_KEY.toRSAPublicKey();
        } catch (Exception e) {
            throw new IllegalStateException("public key unavailable", e);
        }
    }

    public static String admin() {
        return token(Set.of("ADMIN"), "admin-user", FIXED_SUBJECT);
    }

    public static String terminalOp() {
        return token(Set.of("TERMINAL_OP"), "op-user", UUID.randomUUID().toString());
    }

    public static String reader() {
        return token(Set.of("READONLY"), "viewer-user", UUID.randomUUID().toString());
    }

    public static String supervisor() {
        return token(Set.of("SUPERVISOR"), "supervisor-user", UUID.randomUUID().toString());
    }

    public static String token(Set<String> roles, String username, String subject) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .subject(subject)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(Duration.ofMinutes(15))))
                    .claim("roles", roles.stream().toList())
                    .claim("permissions", List.of())
                    .claim("username", username)
                    .claim("email", username + "@smartportx.test")
                    .build();
            JWSHeader header = new JWSHeader(JWSAlgorithm.RS256);
            JWSSigner signer = new RSASSASigner(RSA_KEY.toPrivateKey());
            SignedJWT signed = new SignedJWT(header, claims);
            signed.sign(signer);
            return signed.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("token signing failed", e);
        }
    }

    private static RSAKey generateKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey(keyPair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("RSA key generation failed", e);
        }
    }
}