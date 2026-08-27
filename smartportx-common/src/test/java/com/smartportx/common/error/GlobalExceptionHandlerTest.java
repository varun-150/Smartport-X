package com.smartportx.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/containers");

    @Test
    void notFoundMapsTo404WithRfc9457Body() {
        ResponseEntity<ProblemDetail> response = handler.handleApi(new NotFoundException("container CN0000001 not found"), request);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getType().toString()).isEqualTo("urn:smartportx:problems:not_found");
        assertThat(response.getBody().getInstance().toString()).isEqualTo("/api/v1/containers");
    }

    @Test
    void conflictMapsTo409() {
        ResponseEntity<ProblemDetail> response = handler.handleApi(new ConflictException("duplicate container number"), request);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void validationFieldsReportedAs400FieldMap() {
        BindException bind = new BindException(new BeanPropertyBindingResult(new Object(), "dto"));
        bind.addError(new FieldError("dto", "containerNumber", "must match ISO 6346"));
        bind.addError(new FieldError("dto", "sizeType", "must not be blank"));
        ResponseEntity<ProblemDetail> response = handler.handleBinding(bind, request);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) response.getBody().getProperties().get("fieldErrors");
        assertThat(fields).containsEntry("containerNumber", "must match ISO 6346");
        assertThat(fields).containsEntry("sizeType", "must not be blank");
    }

    @Test
    void serverErrorNeverLeaksMessageOrSecrets() {
        String secret = "password=sup3r-s3cret";
        String sensitive = "secret=very_hidden";
        Throwable ex = new RuntimeException("boom: " + secret + " " + sensitive, new IllegalStateException(secret));
        ResponseEntity<ProblemDetail> response = handler.handleUnexpected(ex, request);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getDetail()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().toString()).doesNotContain(secret).doesNotContain(sensitive);
        assertThat(response.getBody().getProperties()).containsKey("errorId");
    }

    @Test
    void validationProblemCarriesNoExtraLeak() {
        ProblemDetail pd = ApiProblems.validation(Map.of("email", "must be a valid email"));
        assertThat(pd.getStatus()).isEqualTo(400);
        assertThat(pd.getProperties().get("code")).isEqualTo("validation_failed");
        assertThat(pd.getDetail()).doesNotContain("password");
    }
}