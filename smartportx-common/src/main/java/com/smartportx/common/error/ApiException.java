package com.smartportx.common.error;

/**
 * Base runtime exception mapped by {@link GlobalExceptionHandler} to an RFC 9457 response.
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final java.util.Map<String, Object> properties;

    public ApiException(int status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(int status, String code, String message, java.util.Map<String, Object> properties) {
        super(message);
        this.status = status;
        this.code = code;
        this.properties = properties;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public java.util.Map<String, Object> getProperties() {
        return properties;
    }
}