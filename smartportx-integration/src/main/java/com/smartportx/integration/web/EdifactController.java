package com.smartportx.integration.web;

import com.smartportx.integration.domain.EdifactMessage;
import com.smartportx.integration.parser.ParsedEdifact;
import com.smartportx.integration.service.EdifactIngestService;
import com.smartportx.integration.support.IntegrationIds;
import com.smartportx.integration.web.dto.IntegrationDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Inbound EDIFACT endpoint surface (API-CONTRACTS &sect;8 edifact/parse + superset read).
 */
@RestController
@RequestMapping("/api/v1/integration/edifact")
public class EdifactController {

    private final EdifactIngestService ingest;

    public EdifactController(EdifactIngestService ingest) {
        this.ingest = ingest;
    }

    @PostMapping("/parse")
    @PreAuthorize("hasAnyRole('ADMIN','TERMINAL_OP') or hasAuthority('integration:write')")
    public ResponseEntity<IntegrationDtos.ParseResponse> parse(
            @Valid @RequestBody IntegrationDtos.ParseRequest request) {
        EdifactMessage message = ingest.ingest(request.messageType(), request.rawMessage());
        return ResponseEntity.status(HttpStatus.CREATED).body(IntegrationDtos.ParseResponse.of(
                message.getMessageType(), message.getId(), message.getParsedData()));
    }

    @GetMapping("/messages/{messageId}")
    @PreAuthorize("isAuthenticated() or hasAuthority('integration:read')")
    public ResponseEntity<IntegrationDtos.ParseResponse> getMessage(@PathVariable String messageId) {
        UUID id = IntegrationIds.toUuid(messageId, IntegrationIds.MESSAGE_PREFIX);
        EdifactMessage message = ingest.findMessage(id);
        return ResponseEntity.ok(IntegrationDtos.ParseResponse.of(
                message.getMessageType(), message.getId(), message.getParsedData()));
    }
}
