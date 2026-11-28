package com.smartportx.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.smartportx.common.event.DomainEvent;
import com.smartportx.notification.domain.NotificationCategory;
import com.smartportx.notification.domain.NotificationSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Maps a consumed domain event to zero or more STOMP topics (API-CONTRACTS &sect;4.3)
 * and fans each out through {@link NotificationBroadcaster}. Severe events are also
 * forwarded to the {@link AlertEngine}, which deduplicates and emits to
 * {@code /topic/alerts[/{severity}]}.
 */
@Service
public class NotificationEventRouter {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationEventRouter.class);

    private static final String TOPIC_CONTAINERS = "/topic/containers/";
    private static final String TOPIC_YARD_STATUS = "/topic/yard-status";
    private static final String TOPIC_YARD_ZONE = "/topic/yard-zone/";
    private static final String TOPIC_GATE_STATUS = "/topic/gate-status";
    private static final String TOPIC_GATE_LANE = "/topic/gate-lane/";
    private static final String TOPIC_APPOINTMENTS = "/topic/appointments/";
    private static final String TOPIC_VESSEL_STATUS = "/topic/vessel-status";
    private static final String TOPIC_VESSEL = "/topic/vessel/";
    private static final String TOPIC_CUSTOMS = "/topic/customs/";
    private static final String TOPIC_DASHBOARD = "/topic/dashboard/summary";

    private final NotificationBroadcaster broadcaster;
    private final AlertEngine alertEngine;

    public NotificationEventRouter(NotificationBroadcaster broadcaster, AlertEngine alertEngine) {
        this.broadcaster = broadcaster;
        this.alertEngine = alertEngine;
    }

    public void handleEvent(DomainEvent<JsonNode> event) {
        JsonNode payload = event.payload() == null ? NullNode.getInstance() : event.payload();
        List<NotificationPush> pushes = route(event.eventType(), payload);
        if (pushes.isEmpty()) {
            LOG.debug("no STOMP route for eventType={}; skipped", event.eventType());
            return;
        }
        for (NotificationPush push : pushes) {
            broadcaster.broadcast(push);
        }
        NotificationSeverity worst = pushes.stream()
                .map(NotificationPush::severity)
                .max(Comparator.naturalOrder())
                .orElse(NotificationSeverity.INFO);
        if (worst.compareTo(NotificationSeverity.WARNING) >= 0) {
            alertEngine.routeAlert(event.eventType(), payload, worst);
        }
    }

    List<NotificationPush> route(String eventType, JsonNode payload) {
        List<NotificationPush> pushes = new ArrayList<>();
        switch (eventType) {
            // container.*  -> /topic/containers/{containerNumber}
            case "container.ContainerRegistered":
                pushes.add(push(TOPIC_CONTAINERS + text(payload, "containerNumber"),
                        NotificationCategory.CONTAINER_EVENT, NotificationSeverity.INFO,
                        "Container Registered", humanize(payload, "Container ", text(payload, "containerNumber"), " registered"), null, payload));
                break;
            case "container.ContainerStatusChanged":
                pushes.add(push(TOPIC_CONTAINERS + text(payload, "containerNumber"),
                        NotificationCategory.CONTAINER_EVENT, NotificationSeverity.INFO,
                        "Container Status Changed",
                        humanize(payload, "Container ", text(payload, "containerNumber"), " status -> " + text(payload, "newStatus")), null, payload));
                break;
            case "container.ContainerMoved":
                pushes.add(push(TOPIC_CONTAINERS + text(payload, "containerNumber"),
                        NotificationCategory.CONTAINER_EVENT, NotificationSeverity.INFO,
                        "Container Moved",
                        humanize(payload, "Container ", text(payload, "containerNumber"), " moved " + text(payload, "from") + " -> " + text(payload, "to")), null, payload));
                break;
            case "container.ContainerRemoved":
                pushes.add(push(TOPIC_CONTAINERS + text(payload, "containerNumber"),
                        NotificationCategory.CONTAINER_EVENT, NotificationSeverity.INFO,
                        "Container Removed",
                        humanize(payload, "Container ", text(payload, "containerNumber"), " removed"), null, payload));
                break;

            // yard.*
            case "yard.YardPositionAssigned":
                pushes.add(push(TOPIC_YARD_STATUS, NotificationCategory.YARD_UPDATE, NotificationSeverity.INFO,
                        "Yard Position Assigned",
                        humanize(payload, "Container ", text(payload, "containerId"), " assigned to ", text(payload, "position")), null, payload));
                pushes.add(push(TOPIC_YARD_ZONE + text(payload, "blockCode"), NotificationCategory.YARD_UPDATE, NotificationSeverity.INFO,
                        "Yard Position Assigned",
                        humanize(payload, "Container ", text(payload, "containerId"), " assigned to ", text(payload, "position")), null, payload));
                break;
            case "yard.YardPositionFreed":
                pushes.add(push(TOPIC_YARD_STATUS, NotificationCategory.YARD_UPDATE, NotificationSeverity.INFO,
                        "Yard Position Freed",
                        humanize(payload, "Position ", text(payload, "position"), " freed"), null, payload));
                break;
            case "yard.YardOptimizationRun":
                pushes.add(push(TOPIC_YARD_STATUS, NotificationCategory.YARD_UPDATE, NotificationSeverity.INFO,
                        "Yard Optimization Run",
                        humanize(payload, "Yard optimization ", text(payload, "strategy"), " reshuffling reduction ", text(payload, "reshufflingReduction")), null, payload));
                pushes.add(push(TOPIC_DASHBOARD, NotificationCategory.YARD_UPDATE, NotificationSeverity.INFO,
                        "Yard Optimization Run",
                        humanize(payload, "Yard optimization ", text(payload, "strategy"), " reshuffling reduction ", text(payload, "reshufflingReduction")), null, payload));
                break;
            case "yard.EquipmentStatusChanged":
                pushes.add(push(TOPIC_YARD_STATUS, NotificationCategory.YARD_UPDATE, NotificationSeverity.defaultFrom(text(payload, "newStatus")),
                        "Equipment Status Changed",
                        humanize(payload, "Equipment ", text(payload, "code"), " status -> ", text(payload, "newStatus")), null, payload));
                break;

            // gate.*
            case "gate.AppointmentBooked":
            case "gate.AppointmentConfirmed":
            case "gate.AppointmentCancelled":
            case "gate.AppointmentRescheduled":
                pushes.add(push(TOPIC_APPOINTMENTS + text(payload, "appointmentId"),
                        NotificationCategory.APPOINTMENT, NotificationSeverity.INFO,
                        titleFor(eventType),
                        humanize(payload, "Appointment ", text(payload, "confirmationNumber"), " " + raster(eventType), " for ", text(payload, "containerNumber")), null, payload));
                break;
            case "gate.GateCheckIn":
                pushes.add(push(TOPIC_GATE_LANE + text(payload, "gateLane"), NotificationCategory.GATE_EVENT, NotificationSeverity.INFO,
                        "Gate Check In",
                        humanize(payload, "Container ", text(payload, "containerNumber"), " checked in at lane ", text(payload, "gateLane")), null, payload));
                pushes.add(push(TOPIC_GATE_STATUS, NotificationCategory.GATE_EVENT, NotificationSeverity.INFO,
                        "Gate Check In",
                        humanize(payload, "Container ", text(payload, "containerNumber"), " checked in at lane ", text(payload, "gateLane")), null, payload));
                break;
            case "gate.GateCheckOut":
                pushes.add(push(TOPIC_GATE_LANE + text(payload, "gateLane"), NotificationCategory.GATE_EVENT, NotificationSeverity.INFO,
                        "Gate Check Out",
                        humanize(payload, "Container ", text(payload, "containerNumber"), " checked out at lane ", text(payload, "gateLane")), null, payload));
                pushes.add(push(TOPIC_GATE_STATUS, NotificationCategory.GATE_EVENT, NotificationSeverity.INFO,
                        "Gate Check Out",
                        humanize(payload, "Container ", text(payload, "containerNumber"), " checked out at lane ", text(payload, "gateLane")), null, payload));
                break;
            case "gate.GateQueueUpdated":
                pushes.add(push(TOPIC_GATE_STATUS, NotificationCategory.GATE_EVENT, NotificationSeverity.INFO,
                        "Gate Queue Updated",
                        humanize(payload, "Gate queue updated · waiting ", text(payload, "waitingCount"), ""), null, payload));
                pushes.add(push(TOPIC_DASHBOARD, NotificationCategory.GATE_EVENT, NotificationSeverity.INFO,
                        "Gate Queue Updated",
                        humanize(payload, "Gate queue updated · waiting ", text(payload, "waitingCount"), ""), null, payload));
                break;

            // vessel.*
            case "vessel.VesselArrived":
                pushes.add(push(TOPIC_VESSEL + text(payload, "vesselId"), NotificationCategory.VESSEL_UPDATE, NotificationSeverity.INFO,
                        "Vessel Arrived",
                        humanize(payload, "Vessel ", text(payload, "vesselName"), " arrived at berth ", text(payload, "berthNumber")), null, payload));
                pushes.add(push(TOPIC_VESSEL_STATUS, NotificationCategory.VESSEL_UPDATE, NotificationSeverity.INFO,
                        "Vessel Arrived",
                        humanize(payload, "Vessel ", text(payload, "vesselName"), " arrived at berth ", text(payload, "berthNumber")), null, payload));
                break;
            case "vessel.VesselDeparted":
                pushes.add(push(TOPIC_VESSEL + text(payload, "vesselId"), NotificationCategory.VESSEL_UPDATE, NotificationSeverity.INFO,
                        "Vessel Departed",
                        humanize(payload, "Vessel ", text(payload, "vesselName"), " departed"), null, payload));
                pushes.add(push(TOPIC_VESSEL_STATUS, NotificationCategory.VESSEL_UPDATE, NotificationSeverity.INFO,
                        "Vessel Departed",
                        humanize(payload, "Vessel ", text(payload, "vesselName"), " departed"), null, payload));
                break;
            case "vessel.DischargePlanCreated":
                pushes.add(push(TOPIC_VESSEL_STATUS, NotificationCategory.VESSEL_UPDATE, NotificationSeverity.INFO,
                        "Discharge Plan Created",
                        humanize(payload, "Discharge plan ", text(payload, "planId"), " created for vessel ", text(payload, "vesselId")), null, payload));
                break;
            case "vessel.DischargeProgressUpdated":
                pushes.add(push(TOPIC_VESSEL_STATUS, NotificationCategory.VESSEL_UPDATE, NotificationSeverity.INFO,
                        "Discharge Progress Updated",
                        humanize(payload, "Discharge progress ", text(payload, "dischargedCount"), "/", text(payload, "totalContainers")), null, payload));
                break;

            // integration.*
            case "integration.EdifactParsed":
                pushes.add(push(TOPIC_VESSEL_STATUS, NotificationCategory.VESSEL_UPDATE, NotificationSeverity.INFO,
                        "EDIFACT Parsed",
                        humanize(payload, "EDIFACT message parsed: ", text(payload, "messageType"), ""), null, payload));
                break;
            case "integration.DischargeOrderReceived":
                pushes.add(push(TOPIC_VESSEL_STATUS, NotificationCategory.VESSEL_UPDATE, NotificationSeverity.INFO,
                        "Discharge Order Received",
                        humanize(payload, "Discharge order received: ", text(payload, "routeCode"), ""), null, payload));
                break;
            case "integration.ArrivalAnnounced":
                pushes.add(push(TOPIC_CONTAINERS + text(payload, "containerNumber"),
                        NotificationCategory.CONTAINER_EVENT, NotificationSeverity.INFO,
                        "Arrival Announced",
                        humanize(payload, "Arrival announced for container ", text(payload, "containerNumber"), ""), null, payload));
                pushes.add(push(TOPIC_VESSEL_STATUS, NotificationCategory.VESSEL_UPDATE, NotificationSeverity.INFO,
                        "Arrival Announced",
                        humanize(payload, "Arrival announced for container ", text(payload, "containerNumber"), ""), null, payload));
                break;
            case "integration.IotAlertTriggered":
                pushes.add(push(TOPIC_CONTAINERS + text(payload, "containerNumber"),
                        NotificationCategory.IOT, NotificationSeverity.from(text(payload, "alertLevel")),
                        "IoT Alert",
                        humanize(payload, "IoT ", text(payload, "sensorType"), " alert: ", text(payload, "message")), null, payload));
                break;
            case "integration.CustomsStatusChanged":
                pushes.add(push(TOPIC_CUSTOMS + text(payload, "containerNumber"),
                        NotificationCategory.CUSTOMS, NotificationSeverity.from(text(payload, "newStatus")),
                        "Customs Status Changed",
                        humanize(payload, "Customs status ", text(payload, "newStatus"), " for container ", text(payload, "containerNumber")), null, payload));
                break;

            default:
                // Unknown event types are ignored (fail-open by omission, logged upstream).
                break;
        }
        return pushes;
    }

    private NotificationPush push(String topic, NotificationCategory category, NotificationSeverity severity,
                                  String title, String message, String actionUrl, JsonNode data) {
        return new NotificationPush(topic, category, severity, title, message, actionUrl, data);
    }

    private static String titleFor(String eventType) {
        switch (eventType) {
            case "gate.AppointmentBooked":
                return "Appointment Booked";
            case "gate.AppointmentConfirmed":
                return "Appointment Confirmed";
            case "gate.AppointmentCancelled":
                return "Appointment Cancelled";
            case "gate.AppointmentRescheduled":
                return "Appointment Rescheduled";
            default:
                return "Appointment Update";
        }
    }

    private static String raster(String eventType) {
        switch (eventType) {
            case "gate.AppointmentBooked":
                return "booked";
            case "gate.AppointmentConfirmed":
                return "confirmed";
            case "gate.AppointmentCancelled":
                return "cancelled";
            case "gate.AppointmentRescheduled":
                return "rescheduled";
            default:
                return "updated";
        }
    }

    private static String humanize(JsonNode payload, String a, String b, String c) {
        return a + nonBlank(b) + c;
    }

    private static String humanize(JsonNode payload, String a, String b, String c, String d) {
        return a + nonBlank(b) + c + nonBlank(d);
    }

    private static String humanize(JsonNode payload, String a, String b, String c, String d, String e) {
        return a + nonBlank(b) + c + nonBlank(d) + e;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || field == null || field.isBlank()) {
            return "";
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}