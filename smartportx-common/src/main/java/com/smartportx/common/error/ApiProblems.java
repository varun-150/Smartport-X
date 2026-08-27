package com.smartportx.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import com.smartportx.common.correlation.CorrelationContext;

/**
 * Builds RFC 9457 {@link ProblemDetail} bodies with SmartPortX enrichment:
 * {@code errorId}, {@code correlationId}, {@code instance} (= request URI) and
 * optional {@code fieldErrors}. Server errors never leak exception detail.
 */
public final class ApiProblems {

    private static final String PROBLEM_NS = "urn:smartportx:problems:";

    private ApiProblems() {
    }

    public static ProblemDetail create(int status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
        pd.setType(URI.create(PROBLEM_NS + code));
        pd.setTitle(HttpStatus.valueOf(status).getReasonPhrase());
        pd.setProperty("code", code);
        return pd;
    }

    public static ProblemDetail validation(Map<String, String> fieldErrors) {
        ProblemDetail pd = create(400, "validation_failed", "Request validation failed");
        pd.setProperty("fieldErrors", fieldErrors);
        return pd;
    }

    public static ProblemDetail notFound(String detail) {
        return create(404, "not_found", detail);
    }

    public static ProblemDetail conflict(String detail) {
        return create(409, "conflict", detail);
    }

    /** 500: generic message + unique errorId. Never echoes the exception. */
    public static ProblemDetail serverError() {
        ProblemDetail pd = create(500, "internal_error", "An unexpected error occurred");
        pd.setProperty("errorId", UUID.randomUUID().toString());
        return pd;
    }

    /** Enrich a built body with request correlation context. */
    public static ProblemDetail enrich(ProblemDetail pd, HttpServletRequest request) {
        CorrelationContext.current().ifPresent(id -> pd.setProperty("correlationId", id));
        if (request != null) {
            pd.setInstance(URI.create(request.getRequestURI()));
        }
        return pd;
    }
}