package com.smartportx.integration.parser;

import java.time.Instant;
import java.util.List;

/**
 * Structured result of parsing an inbound EDIFACT message. Mirrors the API-CONTRACTS
 * &sect;8 parse response and carries the message-specific payloads a mapper turns into
 * domain events.
 */
public record ParsedEdifact(
        String messageType,
        String messageReference,
        String messageDate,
        String messageTime,
        String sender,
        String receiver,
        String interchangeRef,
        String carrierName,
        String carrierCode,
        String carrierCountry,
        String vesselName,
        String vesselImo,
        String portOfCall,
        String terminal,
        Instant eta,
        List<EdifactContainer> containers,
        List<String> validationWarnings) {
}
