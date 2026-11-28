package com.smartportx.container.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID eventId,
        String eventType,
        UUID containerId,
        String containerNumber,
        Instant timestamp,
        JsonNode data,
        String source) {
}