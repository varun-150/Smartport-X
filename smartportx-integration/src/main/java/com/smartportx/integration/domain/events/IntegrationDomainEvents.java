package com.smartportx.integration.domain.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Domain event vocabulary for the integration service. Events are {@code integration.*}. */
public final class IntegrationDomainEvents {

    public static final String EDIFACT_PARSED = "integration.EdifactParsed";
    public static final String DISCHARGE_ORDER_RECEIVED = "integration.DischargeOrderReceived";
    public static final String ARRIVAL_ANNOUNCED = "integration.ArrivalAnnounced";
    public static final String IOT_ALERT_TRIGGERED = "integration.IotAlertTriggered";
    public static final String CUSTOMS_STATUS_CHANGED = "integration.CustomsStatusChanged";

    private IntegrationDomainEvents() {
    }

    public record EdifactParsedPayload(UUID messageId, String messageType, String messageReference,
                                       String correlationId, List<String> segments) {
    }

    public record ContainerUnit(UUID id, String containerNumber, String isoType, String status,
                                String operator, String yardPosition) {
    }

    public record DischargeOrderPayload(UUID messageId, String messageReference, String vesselName,
                                        String vesselImo, String portOfCall, String terminal,
                                        String carrier, Instant eta, List<ContainerUnit> containers,
                                        List<String> validationWarnings) {
    }

    public record ArrivalAnnouncementPayload(UUID messageId, String messageReference, String containerNumber,
                                             String isoType, String origin, String destination,
                                             String operator, String customsStatus, Instant plannedArrival) {
    }

    public record IotAlertPayload(String containerNumber, String sensorType, String alertLevel,
                                  String message, Instant observedAt) {
    }

    public record CustomsChangedPayload(String containerNumber, String previousStatus,
                                        String newStatus, String reason, Instant changedAt) {
    }
}
