package com.smartportx.identity.error;

public class PasswordPolicyViolation extends RuntimeException {
    public PasswordPolicyViolation(String message) {
        super(message);
    }
}