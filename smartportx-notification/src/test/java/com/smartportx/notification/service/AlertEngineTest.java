package com.smartportx.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartportx.notification.domain.NotificationSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AlertEngineTest {

    private NotificationBroadcaster broadcaster;
    private NotificationClock clock;
    private AlertEngine engine;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Instant fixedNow;

    @BeforeEach
    void setUp() {
        broadcaster = mock(NotificationBroadcaster.class);
        clock = mock(NotificationClock.class);
        fixedNow = Instant.parse("2026-01-15T10:00:00Z");
        lenient().when(clock.now()).thenReturn(fixedNow);
        engine = new AlertEngine(broadcaster, clock, 300);
    }

    @Test
    void warningSeverity_broadcastsAlert() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of("code", "CG-01", "newStatus", "OUT_OF_SERVICE"));

        engine.routeAlert("yard.EquipmentStatusChanged", payload, NotificationSeverity.WARNING);

        verify(broadcaster).broadcastAlert(
                eq(NotificationSeverity.WARNING),
                eq("Equipment Out of Service"),
                eq("Equipment CG-01 is OUT_OF_SERVICE"),
                isNull(),
                eq(payload)
        );
    }

    @Test
    void criticalSeverity_broadcastsAlert() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of(
                "containerNumber", "MSKU123",
                "sensorType", "temperature",
                "message", "overheat detected"
        ));

        engine.routeAlert("integration.IotAlertTriggered", payload, NotificationSeverity.CRITICAL);

        verify(broadcaster).broadcastAlert(
                eq(NotificationSeverity.CRITICAL),
                eq("IoT Sensor Alert"),
                eq("temperature alert on container MSKU123: overheat detected"),
                isNull(),
                eq(payload)
        );
    }

    @Test
    void infoSeverity_doesNotBroadcast() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of("foo", "bar"));

        engine.routeAlert("some.Event", payload, NotificationSeverity.INFO);

        verifyNoInteractions(broadcaster);
    }

    @Test
    void nullSeverity_doesNotBroadcast() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of("foo", "bar"));

        engine.routeAlert("some.Event", payload, null);

        verifyNoInteractions(broadcaster);
    }

    @Test
    void deduplicatesWithinWindow() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of("code", "CG-01", "newStatus", "OUT_OF_SERVICE"));

        engine.routeAlert("yard.EquipmentStatusChanged", payload, NotificationSeverity.WARNING);
        engine.routeAlert("yard.EquipmentStatusChanged", payload, NotificationSeverity.WARNING);

        verify(broadcaster, times(1)).broadcastAlert(any(), any(), any(), any(), any());
    }

    @Test
    void doesNotDeduplicateAfterWindowExpires() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of("code", "CG-01", "newStatus", "OUT_OF_SERVICE"));

        engine.routeAlert("yard.EquipmentStatusChanged", payload, NotificationSeverity.WARNING);

        // Advance clock past the 300-second window
        when(clock.now()).thenReturn(fixedNow.plusSeconds(301));

        engine.routeAlert("yard.EquipmentStatusChanged", payload, NotificationSeverity.WARNING);

        verify(broadcaster, times(2)).broadcastAlert(any(), any(), any(), any(), any());
    }

    @Test
    void differentPayloadsNotDeduplicated() throws Exception {
        JsonNode payload1 = objectMapper.valueToTree(Map.of("code", "CG-01", "newStatus", "OUT_OF_SERVICE"));
        JsonNode payload2 = objectMapper.valueToTree(Map.of("code", "CG-02", "newStatus", "OUT_OF_SERVICE"));

        engine.routeAlert("yard.EquipmentStatusChanged", payload1, NotificationSeverity.WARNING);
        engine.routeAlert("yard.EquipmentStatusChanged", payload2, NotificationSeverity.WARNING);

        verify(broadcaster, times(2)).broadcastAlert(any(), any(), any(), any(), any());
    }

    @Test
    void differentEventTypesNotDeduplicated() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of("containerNumber", "MSKU123"));

        engine.routeAlert("integration.IotAlertTriggered", payload, NotificationSeverity.WARNING);
        engine.routeAlert("integration.CustomsStatusChanged", payload, NotificationSeverity.WARNING);

        verify(broadcaster, times(2)).broadcastAlert(any(), any(), any(), any(), any());
    }

    @Test
    void resetClearsDeduplicationState() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of("code", "CG-01", "newStatus", "OUT_OF_SERVICE"));

        engine.routeAlert("yard.EquipmentStatusChanged", payload, NotificationSeverity.WARNING);
        engine.reset();
        engine.routeAlert("yard.EquipmentStatusChanged", payload, NotificationSeverity.WARNING);

        verify(broadcaster, times(2)).broadcastAlert(any(), any(), any(), any(), any());
    }

    @Test
    void customsStatusChanged_formatsCorrectMessage() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of(
                "containerNumber", "MSKU123",
                "newStatus", "HELD"
        ));

        engine.routeAlert("integration.CustomsStatusChanged", payload, NotificationSeverity.WARNING);

        verify(broadcaster).broadcastAlert(
                eq(NotificationSeverity.WARNING),
                eq("Customs Hold"),
                eq("Customs status HELD for container MSKU123"),
                isNull(),
                eq(payload)
        );
    }

    @Test
    void unknownEventType_usesGenericTitleAndMessage() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of("foo", "bar"));

        engine.routeAlert("some.UnknownEvent", payload, NotificationSeverity.WARNING);

        verify(broadcaster).broadcastAlert(
                eq(NotificationSeverity.WARNING),
                eq("Operations Alert"),
                eq("some.UnknownEvent requires attention"),
                isNull(),
                eq(payload)
        );
    }
}