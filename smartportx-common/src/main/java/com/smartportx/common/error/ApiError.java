package com.smartportx.common.error;

import java.util.Map;

/**
 * RFC 9457 error descriptor used across SmartPortX services.
 * {@code code} is a stable machine-readable identifier (e.g. {@code validation_failed},
 * {@code conflict}), {@code message} a human summary, {@code fieldErrors} an optional
 * {@code field -> message} map for validation failures (see M2.3).
 */
public record ApiError(String code, String message, Map<String, String> fieldErrors) {

    public ApiError {
        fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Map.of());
    }
}