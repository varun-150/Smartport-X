package com.smartportx.common.error;

/** Maps to 409 via {@link GlobalExceptionHandler}. */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(409, "conflict", message);
    }
}