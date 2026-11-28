package com.smartportx.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartportx.notification.domain.NotificationSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Latches severe events (severity &gt;= WARNING) to the alert topics
 * {@code /topic/alerts} and {@code /topic/alerts/{severity}}. Events are deduplicated
 * within a configurable window keyed by {@code eventType} + a payload selector
 * (e.g. containerNumber, vesselId or blockCode) so repeated IOT / equipment / customs
 * pings do not spam subscribers. ALERT history records are persisted for auditability.
 */
@Service
public class AlertEngine {

    private static final Logger LOG = LoggerFactory.getLogger(AlertEngine.class);

    private final NotificationBroadcaster broadcaster;
    private final NotificationClock clock;
    private final Duration dedupeWindow;
    private final ConcurrentHashMap<String, Instant> lastPublished = new ConcurrentHashMap<>();

    public AlertEngine(NotificationBroadcaster broadcaster, NotificationClock clock,
                       @Value("${smartportx.notification.alert.dedupe-window-seconds:300}") long dedupeWindowSeconds) {
        this.broadcaster = broadcaster;
        this.clock = clock;
        this.dedupeWindow = Duration.ofSeconds(dedupeWindowSeconds);
    }

    public void routeAlert(String eventType, JsonNode payload, NotificationSeverity severity) {
        if (severity == null || severity.compareTo(NotificationSeverity.WARNING) < 0) {
            return;
        }
        String key = dedupeKey(eventType, payload);
        Instant now = clock.now();
        Instant previous = lastPublished.get(key);
        if (previous != null && now.isBefore(previous.plus(dedupeWindow))) {
            LOG.debug("alert deduplicated within window for {}", key);
            return;
        }
        lastPublished.put(key, now);
        broadcaster.broadcastAlert(severity, titleFor(eventType), messageFor(eventType, payload), null, payload);
        LOG.info("alert published {} for {} (severity={})", eventType, key, severity);
    }

    private String dedupeKey(String eventType, JsonNode payload) {
        String selector = text(payload, "containerNumber");
        if (selector.isBlank()) {
            selector = text(payload, "vesselId");
        }
        if (selector.isBlank()) {
            selector = text(payload, "blockCode");
        }
        if (selector.isBlank()) {
            selector = text(payload, "gateLane");
        }
        if (selector.isBlank()) {
            selector = text(payload, "appointmentId");
        }
        if (selector.isBlank()) {
            selector = text(payload, "code");
        }
        return eventType + "|" + selector;
    }

    private String titleFor(String eventType) {
        switch (eventType) {
            case "integration.IotAlertTriggered":
                return "IoT Sensor Alert";
            case "yard.EquipmentStatusChanged":
                return "Equipment Out of Service";
            case "integration.CustomsStatusChanged":
                return "Customs Hold";
            default:
                return "Operations Alert";
        }
    }

    private String messageFor(String eventType, JsonNode payload) {
        switch (eventType) {
            case "integration.IotAlertTriggered":
                return text(payload, "sensorType") + " alert on container "
                        + text(payload, "containerNumber") + ": " + text(payload, "message");
            case "yard.EquipmentStatusChanged":
                return "Equipment " + text(payload, "code") + " is " + text(payload, "newStatus");
            case "integration.CustomsStatusChanged":
                return "Customs status " + text(payload, "newStatus")
                        + " for container " + text(payload, "containerNumber");
            default:
                return eventType + " requires attention";
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || field == null || field.isBlank()) {
            return "";
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    /** Test seam: clears the deduplication state. */
    public void reset() {
        lastPublished.clear();
    }
}