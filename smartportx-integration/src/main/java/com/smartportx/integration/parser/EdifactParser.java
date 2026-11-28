package com.smartportx.integration.parser;

import com.smartportx.common.error.ApiException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Hand-rolled EDIFACT semantic parser for COPRAR (Container Pick-up/Discharge Report)
 * and COPARN (Container Arrival Notification). Tokenises via {@link EdifactTokeniser},
 * validates the interchange envelope and maps the known segments onto a
 * {@link ParsedEdifact}. Per D-008 this implements the subset the platform consumes and
 * does not attempt full UN/EDIFACT compliance.
 *
 * <p>Recognised segments: UNB/UNH/UNT/UNZ envelope, BGM (message number), NAD (carrier),
 * TDT (vessel), LOC (port/terminal), DTM (dates), EQD (equipment/container), RFF.
 */
@Service
public class EdifactParser {

    private static final Set<String> SUPPORTED =
            Set.of("COPRAR", "COPARN", "COARRI", "CODECO");

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss", Locale.ROOT);
    private static final DateTimeFormatter ZONED = DateTimeFormatter.ofPattern("yyyyMMdd:HHmm", Locale.ROOT);

    public ParsedEdifact parse(String messageType, String rawMessage) {
        if (messageType == null || messageType.isBlank()) {
            throw new ApiException(400, "invalid_message_type", "messageType is required");
        }
        String normalized = messageType.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED.contains(normalized)) {
            throw new ApiException(400, "unsupported_message_type",
                    "Unsupported message type '" + messageType + "'. Supported: COPRAR, COPARN, COARRI, CODECO");
        }
        if (rawMessage == null || rawMessage.isBlank()) {
            throw new ApiException(400, "malformed_message", "rawMessage is required and must not be blank");
        }
        List<EdifactSegment> segments = EdifactTokeniser.tokenise(rawMessage);
        validateEnvelope(segments, normalized);
        return map(normalized, segments);
    }

    private void validateEnvelope(List<EdifactSegment> segments, String expectedType) {
        List<String> errors = new ArrayList<>();
        if (segments.isEmpty()) {
            throw new ApiException(400, "malformed_message", "Message contains no EDIFACT segments");
        }
        boolean hasUnb = segments.stream().anyMatch(s -> s.tag().equals("UNB"));
        boolean hasUnh = segments.stream().anyMatch(s -> s.tag().equals("UNH"));
        boolean hasUnt = segments.stream().anyMatch(s -> s.tag().equals("UNT"));
        if (!hasUnb) {
            errors.add("Missing UNB interchange header");
        }
        if (!hasUnh) {
            errors.add("Missing UNH message header");
        }
        if (!hasUnt) {
            errors.add("Missing UNT message trailer");
        }
        segments.stream()
                .filter(s -> s.tag().equals("UNH"))
                .map(s -> s.member(1, 0))
                .filter(ref -> !ref.isBlank() && !ref.equals(expectedType))
                .findFirst()
                .ifPresent(ref -> errors.add(
                        "UNH message type '" + ref + "' does not match requested messageType '" + expectedType + "'"));
        if (!errors.isEmpty()) {
            throw new ApiException(400, "validation_failed", "EDIFACT envelope validation failed: "
                    + String.join("; ", errors),
                    java.util.Map.of("validationErrors", errors));
        }
    }

    private ParsedEdifact map(String messageType, List<EdifactSegment> segments) {
        String messageReference = segments.stream().filter(s -> s.tag().equals("UNH"))
                .map(s -> s.element(0)).findFirst().orElse("");
        String sender = segments.stream().filter(s -> s.tag().equals("UNB"))
                .map(s -> s.member(1, 0)).findFirst().orElse("");
        String receiver = segments.stream().filter(s -> s.tag().equals("UNB"))
                .map(s -> s.member(2, 0)).findFirst().orElse("");
        String interchangeRef = segments.stream().filter(s -> s.tag().equals("UNB"))
                .map(s -> s.element(4)).findFirst().orElse("");

        String carrierName = "";
        String carrierCode = "";
        String carrierCountry = "";
        for (EdifactSegment s : segments) {
            if (s.tag().equals("NAD")) {
                String qualifier = s.element(0);
                if (("CA".equals(qualifier) || "CN".equals(qualifier)) && carrierCode.isEmpty()) {
                    carrierCode = s.member(1, 0);
                    carrierName = s.member(1, 1);
                    if (carrierName.isEmpty()) {
                        carrierName = s.element(2);
                    }
                } else if ("CO".equals(qualifier) && carrierCountry.isEmpty()) {
                    carrierCountry = s.member(6, 0);
                }
            }
        }

        String vesselName = "";
        String vesselImo = "";
        String portOfCall = "";
        String terminal = "";
        for (EdifactSegment s : segments) {
            if (s.tag().equals("TDT")) {
                if (vesselName.isEmpty()) {
                    vesselName = s.member(3, 0);
                }
                if (vesselImo.isEmpty()) {
                    vesselImo = s.member(5, 0);
                }
            } else if (s.tag().equals("LOC")) {
                String qualifier = s.element(0);
                if ("11".equals(qualifier) && portOfCall.isEmpty()) {
                    portOfCall = s.member(1, 0);
                } else if ("88".equals(qualifier) && terminal.isEmpty()) {
                    terminal = s.member(1, 0);
                }
            }
        }

        Instant eta = null;
        String messageDate = "";
        String messageTime = "";
        for (EdifactSegment s : segments) {
            if (s.tag().equals("DTM")) {
                String qualifier = s.member(0, 0);
                String date = s.member(0, 1);
                String timePart = s.member(0, 2);
                String format = s.element(1);
                if ("ETA".equals(qualifier) || "132".equals(qualifier) || "133".equals(qualifier)) {
                    Instant parsed = parseDateTime(date, timePart, format);
                    if (parsed != null) {
                        eta = parsed;
                    } else if (eta == null && !date.isBlank()) {
                        eta = parseDateOnly(date);
                    }
                } else if ("137".equals(qualifier) && messageDate.isEmpty()) {
                    messageDate = date;
                    messageTime = timePart;
                }
            }
        }

        List<EdifactContainer> containers = new ArrayList<>();
        for (EdifactSegment s : segments) {
            if (s.tag().equals("EQD")) {
                String equipmentType = s.element(1);
                String containerNumber = s.member(2, 0);
                if (containerNumber.isBlank()) {
                    continue;
                }
                String isoType = extractIsoType(equipmentType);
                String status = s.member(3, 0);
                containers.add(new EdifactContainer(containerNumber, isoType, status, "", "", ""));
            }
        }
        // COPARN single-container messages may carry the container in a CNT/GID context;
        // the EQD scan above is the canonical source either way.

        List<String> warnings = new ArrayList<>();
        if (containers.isEmpty()) {
            warnings.add("No EQD container equipment segments found; parse produced an empty container list");
        }

        return new ParsedEdifact(messageType, messageReference, messageDate, messageTime,
                sender, receiver, interchangeRef,
                carrierName, carrierCode, carrierCountry,
                vesselName, vesselImo, portOfCall, terminal,
                eta, List.copyOf(containers), List.copyOf(warnings));
    }

    private String extractIsoType(String equipmentType) {
        if (equipmentType == null || equipmentType.isBlank()) {
            return "";
        }
        String upper = equipmentType.toUpperCase(Locale.ROOT);
        // EDIFACT equipment size/type is a 4-char ISO where the leading digit encodes length.
        if (upper.matches("[0-9]{4}")) {
            return upper;
        }
        // Some producers use the full ISO 6346 code (e.g. 45G1 -> 45G1).
        return upper;
    }

    private Instant parseDateTime(String date, String timePart, String format) {
        if (date.isBlank()) {
            return null;
        }
        if ("203".equals(format) || "204".equals(format)) {
            // CCYYMMDDHHMM (203) / CCYYMMDDHHMMSS (204) without zone offset: EDIFACT
            // interchange times are exchanged as UTC, so resolve against UTC.
            try {
                String value = timePart.isBlank() ? date : date + ":" + timePart;
                return LocalDateTime.parse(value, ZONED).atOffset(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        return null;
    }

    private Instant parseDateOnly(String date) {
        if (date.isBlank()) {
            return null;
        }
        try {
            LocalDate d = LocalDate.parse(date, DATE);
            LocalTime t = LocalTime.parse("235959", TIME);
            return d.atTime(t).toInstant(java.time.ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
