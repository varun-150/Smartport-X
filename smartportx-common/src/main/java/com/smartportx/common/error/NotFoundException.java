package com.smartportx.common.error;

/** Maps to 404 via {@link GlobalExceptionHandler}. */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(404, "not_found", message);
    }
}