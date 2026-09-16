package com.smartportx.integration.web;

import com.smartportx.common.error.ApiException;
import com.smartportx.integration.domain.IotReading;
import com.smartportx.integration.service.IotIngestService;
import com.smartportx.integration.web.dto.IntegrationDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * IoT telemetry endpoint surface (API-CONTRACTS &sect;8 iot/data).
 */
@RestController
@RequestMapping("/api/v1/integration/iot")
public class IotController {

    private final IotIngestService ingest;

    public IotController(IotIngestService ingest) {
        this.ingest = ingest;
    }

    @PostMapping("/data")
    @PreAuthorize("hasAnyRole('ADMIN','TERMINAL_OP') or hasAuthority('integration:write')")
    public ResponseEntity<IntegrationDtos.ReadingResponse> ingestReading(
            @Valid @RequestBody IntegrationDtos.IngestReadingRequest request) {
        IotIngestService.IngestResult result = ingest.ingestResult(new IotIngestService.ReadingInput(
                request.containerNumber(),
                request.sensorType(),
                request.value(),
                parseTimestamp(request.timestamp()),
                request.latitude(),
                request.longitude(),
                request.batteryLevel(),
                request.signalStrength()));
        IotReading reading = result.reading();
        IntegrationDtos.ReadingResponse response = new IntegrationDtos.ReadingResponse(
                reading.getId(), reading.getContainerNumber(), reading.getSensorType(),
                reading.getValue(), reading.getUnit(), reading.getTimestamp().toString(), result.alert());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private Instant parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            throw new ApiException(400, "validation_failed", "timestamp must be an ISO-8601 instant");
        }
    }
}
