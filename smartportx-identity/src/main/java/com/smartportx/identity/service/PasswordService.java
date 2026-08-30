package com.smartportx.identity.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class PasswordService {

    private static final Pattern STRENGTH = Pattern.compile("^(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).+$");

    private final PasswordEncoder encoder;

    public PasswordService(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    public void validatePolicy(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw invalid("Password must be between 8 and 128 characters");
        }
        if (!STRENGTH.matcher(password).matches()) {
            throw invalid("Password must contain at least one uppercase letter, one digit and one special character");
        }
    }

    public String encode(String password) {
        return encoder.encode(password);
    }

    public boolean matches(String raw, String hash) {
        if (raw == null || hash == null) {
            return false;
        }
        return encoder.matches(raw, hash);
    }

    private com.smartportx.identity.error.PasswordPolicyViolation invalid(String message) {
        return new com.smartportx.identity.error.PasswordPolicyViolation(message);
    }
}