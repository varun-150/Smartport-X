package com.smartportx.container.web;

import com.smartportx.common.error.ApiProblems;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Container-specific problem+json mapping: method-security denials from @PreAuthorize
 * surface as AccessDeniedException in the controller layer and are translated to 403
 * here, mirroring the identity service's IdentityExceptionAdvice.
 */
@RestControllerAdvice(basePackages = "com.smartportx.container.web")
public class ContainerExceptionAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> accessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = ApiProblems.enrich(
                ApiProblems.create(403, "access_denied", "Access is denied"), request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}