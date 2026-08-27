package com.smartportx.common.error;

import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception mapping to RFC 9457 {@link ProblemDetail}.
 * Mapping: validation -> 400 + fieldErrors, not-found -> 404, conflict -> 409,
 * unreadable/malformed -> 400, server -> 500 with errorId, never exception text.
 * Registered only in servlet (MVC) services via SmartPortxCommonAutoConfiguration.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest request) {
        ProblemDetail pd = ApiProblems.create(ex.getStatus(), ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(ApiProblems.enrich(pd, request));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ProblemDetail> handleBinding(BindException ex, HttpServletRequest request) {
        return badRequest(fieldErrors(ex.getBindingResult()), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> byField = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        v -> v.getMessage(),
                        (a, b) -> a,
                        LinkedHashMap::new));
        return badRequest(byField, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, IllegalArgumentException.class})
    public ResponseEntity<ProblemDetail> handleMalformed(Exception ex, HttpServletRequest request) {
        // 400 but generic; fieldErrors left empty (avoids echoing attacker-provided text).
        ProblemDetail pd = ApiProblems.create(400, "malformed_request", "Request could not be interpreted");
        return ResponseEntity.badRequest().body(ApiProblems.enrich(pd, request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(404).body(ApiProblems.enrich(ApiProblems.notFound("Resource not found"), request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        return ResponseEntity.status(409).body(ApiProblems.enrich(ApiProblems.conflict("Operation conflicts with existing data"), request));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Throwable ex, HttpServletRequest request) {
        // errorId logged; detail stays generic to prevent info/stack leaks.
        ProblemDetail pd = ApiProblems.serverError();
        String errorId = (String) pd.getProperties().get("errorId");
        // LOGGING_TOKEN: correlation id is enriched later; log with errorId here.
        org.slf4j.LoggerFactory.getLogger(getClass()).error("smartportx-common: unhandled error {} - {}", errorId, ex.getMessage(), ex);
        return ResponseEntity.status(500).body(ApiProblems.enrich(pd, request));
    }

    private ResponseEntity<ProblemDetail> badRequest(Map<String, String> fieldErrors, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiProblems.enrich(ApiProblems.validation(fieldErrors), request));
    }

    static Map<String, String> fieldErrors(BindingResult bindingResult) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : bindingResult.getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return errors;
    }
}