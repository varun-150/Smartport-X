package com.smartportx.integration.web.dto;

import com.smartportx.common.correlation.CorrelationContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Request/response contracts for the integration API (API-CONTRACTS &sect;8 + superset). */
public final class IntegrationDtos {

    private IntegrationDtos() {
    }

    // ----- EDIFACT -----

    public record ParseRequest(
            @NotBlank(message = "messageType is required") String messageType,
            @NotBlank(message = "rawMessage is required") String rawMessage) {
    }

    public record ParseResponse(UUID correlationId, String messageType, UUID messageId,
                                Map<String, Object> parsedData) {
        public static ParseResponse of(String messageType, UUID messageId, Map<String, Object> parsedData) {
            UUID correlation = CorrelationContext.current().map(UUID::fromString).orElse(null);
            return new ParseResponse(correlation, messageType, messageId, parsedData);
        }
    }

    // ----- IoT -----

    public record IngestReadingRequest(
            @NotBlank(message = "containerNumber is required") @Size(max = 11) String containerNumber,
            @NotBlank(message = "sensorType is required") String sensorType,
            @NotBlank(message = "value is required") String value,
            String timestamp,
            Double latitude,
            Double longitude,
            Integer batteryLevel,
            Integer signalStrength) {
    }

    public record ReadingResponse(UUID id, String containerNumber, String sensorType, String value,
                                  String unit, String timestamp, String alert) {
    }

    // ----- Customs -----

    public record CustomsSubmitRequest(
            @NotBlank(message = "containerNumber is required") @Size(max = 11) String containerNumber,
            String declarationNumber,
            String reviewedBy) {
    }

    public record CustomsReviewRequest(
            @NotBlank(message = "decision is required") String decision,
            String reason) {
    }

    public record CustomsStatusResponse(String containerNumber, String status, String reason,
                                        String clearedAt, String reviewedBy, String declarationNumber) {
        public static CustomsStatusResponse of(String containerNumber, String status, String reason,
                                               Instant clearedAt, String reviewedBy, String declarationNumber) {
            return new CustomsStatusResponse(containerNumber, status, reason,
                    clearedAt == null ? null : clearedAt.toString(), reviewedBy, declarationNumber);
        }
    }

    public record GateOutResponse(String containerNumber, String status, boolean allowed, String reason) {
    }
}
