package com.smartportx.identity.web;

import com.smartportx.common.error.ApiProblems;
import com.smartportx.identity.error.CredentialsException;
import com.smartportx.identity.error.InvalidQueryException;
import com.smartportx.identity.error.PasswordPolicyViolation;
import com.smartportx.identity.error.RateLimitException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.smartportx.identity.web")
public class IdentityExceptionAdvice {

    @ExceptionHandler(CredentialsException.class)
    public ResponseEntity<ProblemDetail> credentials(CredentialsException ex, HttpServletRequest request) {
        ProblemDetail problem = ApiProblems.enrich(
                ApiProblems.create(401, "credentials_invalid", ex.getMessage()), request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ProblemDetail> rateLimit(RateLimitException ex, HttpServletRequest request) {
        ProblemDetail problem = ApiProblems.enrich(
                ApiProblems.create(429, "too_many_attempts", ex.getMessage()), request);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(PasswordPolicyViolation.class)
    public ResponseEntity<ProblemDetail> passwordPolicy(PasswordPolicyViolation ex, HttpServletRequest request) {
        ProblemDetail problem = ApiProblems.enrich(
                ApiProblems.create(400, "password_policy", ex.getMessage()), request);
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(InvalidQueryException.class)
    public ResponseEntity<ProblemDetail> invalidQuery(InvalidQueryException ex, HttpServletRequest request) {
        ProblemDetail problem = ApiProblems.enrich(
                ApiProblems.create(400, "invalid_query", ex.getMessage()), request);
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> accessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = ApiProblems.enrich(
                ApiProblems.create(403, "access_denied", "Access is denied"), request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}