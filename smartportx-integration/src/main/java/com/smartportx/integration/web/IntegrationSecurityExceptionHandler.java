package com.smartportx.integration.web;

import com.smartportx.common.error.ApiProblems;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Method-security denials propagate past the servlet filters, so the
 * {@link org.springframework.security.web.access.AccessDeniedHandler} never sees them.
 * This advice maps them to HTTP 403 (RFC 9457).
 */
@RestControllerAdvice
public class IntegrationSecurityExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(403)
                .body(ApiProblems.enrich(ApiProblems.create(403, "access_denied", "Access is denied"), request));
    }
}
