package com.smartportx.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartportx.common.event.DomainEvent;
import com.smartportx.notification.domain.NotificationCategory;
import com.smartportx.notification.domain.NotificationSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationEventRouterTest {

    private NotificationBroadcaster broadcaster;
    private AlertEngine alertEngine;
    private NotificationEventRouter router;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        broadcaster = mock(NotificationBroadcaster.class);
        alertEngine = mock(AlertEngine.class);
        router = new NotificationEventRouter(broadcaster, alertEngine);
    }

    @Test
    void containerRegistered_routesToContainerTopic() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of(
                "containerNumber", "MSKU1234567",
                "containerType", "40HC"
        ));
        DomainEvent<JsonNode> event = DomainEvent.of("container.ContainerRegistered", "smartportx-container", payload);

        router.handleEvent(event);

        ArgumentCaptor<NotificationPush> captor = ArgumentCaptor.forClass(NotificationPush.class);
        verify(broadcaster, times(1)).broadcast(captor.capture());

        NotificationPush push = captor.getValue();
        assertThat(push.topic()).isEqualTo("/topic/containers/MSKU1234567");
        assertThat(push.category()).isEqualTo(NotificationCategory.CONTAINER_EVENT);
        assertThat(push.severity()).isEqualTo(NotificationSeverity.INFO);
        assertThat(push.title()).isEqualTo("Container Registered");
        assertThat(push.message()).contains("MSKU1234567");
    }

    @Test
    void containerStatusChanged_routesToContainerTopic() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of(
                "containerNumber", "MSKU1234567",
                "newStatus", "DISCHARGED"
        ));
        DomainEvent<JsonNode> event = DomainEvent.of("container.ContainerStatusChanged", "smartportx-container", payload);

        router.handleEvent(event);

        ArgumentCaptor<NotificationPush> captor = ArgumentCaptor.forClass(NotificationPush.class);
        verify(broadcaster, times(1)).broadcast(captor.capture());

        NotificationPush push = captor.getValue();
        assertThat(push.topic()).isEqualTo("/topic/containers/MSKU1234567");
        assertThat(push.message()).contains("MSKU1234567").contains("DISCHARGED");
    }

    @Test
    void yardPositionAssigned_routesToYardStatusAndYardZone() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of(
                "containerId", "MSKU123",
                "position", "B1-03-02",
                "blockCode", "B1"
        ));
        DomainEvent<JsonNode> event = DomainEvent.of("yard.YardPositionAssigned", "smartportx-yard", payload);

        router.handleEvent(event);

        verify(broadcaster, times(2)).broadcast(any(NotificationPush.class));
        ArgumentCaptor<NotificationPush> captor = ArgumentCaptor.forClass(NotificationPush.class);
        verify(broadcaster, times(2)).broadcast(captor.capture());

        var pushes = captor.getAllValues();
        assertThat(pushes).anySatisfy(p -> assertThat(p.topic()).isEqualTo("/topic/yard-status"));
        assertThat(pushes).anySatisfy(p -> assertThat(p.topic()).isEqualTo("/topic/yard-zone/B1"));
    }

    @Test
    void equipmentStatusChanged_routesToYardStatus() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of(
                "code", "CG-01",
                "newStatus", "OUT_OF_SERVICE"
        ));
        DomainEvent<JsonNode> event = DomainEvent.of("yard.EquipmentStatusChanged", "smartportx-yard", payload);

        router.handleEvent(event);

        ArgumentCaptor<NotificationPush> captor = ArgumentCaptor.forClass(NotificationPush.class);
        verify(broadcaster, times(1)).broadcast(captor.capture());

        NotificationPush push = captor.getValue();
        assertThat(push.topic()).isEqualTo("/topic/yard-status");
        assertThat(push.severity()).isEqualTo(NotificationSeverity.WARNING);
    }

    @Test
    void equipmentStatusChanged_severityFromPayload() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of(
                "code", "CG-01",
                "newStatus", "AVAILABLE"
        ));
        DomainEvent<JsonNode> event = DomainEvent.of("yard.EquipmentStatusChanged", "smartportx-yard", payload);

        router.handleEvent(event);

        ArgumentCaptor<NotificationPush> captor = ArgumentCaptor.forClass(NotificationPush.class);
        verify(broadcaster, times(1)).broadcast(captor.capture());

        assertThat(captor.getValue().severity()).isEqualTo(NotificationSeverity.INFO);
    }

    @Test
    void gateCheckIn_routesToGateLaneAndGateStatus() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of(
                "containerNumber", "MSKU123",
                "gateLane", "L1"
        ));
        DomainEvent<JsonNode> event = DomainEvent.of("gate.GateCheckIn", "smartportx-gate", payload);

        router.handleEvent(event);

        ArgumentCaptor<NotificationPush> captor = ArgumentCaptor.forClass(NotificationPush.class);
        verify(broadcaster, times(2)).broadcast(captor.capture());

        var pushes = captor.getAllValues();
        assertThat(pushes).anySatisfy(p -> assertThat(p.topic()).isEqualTo("/topic/gate-lane/L1"));
        assertThat(pushes).anySatisfy(p -> assertThat(p.topic()).isEqualTo("/topic/gate-status"));
    }

    @Test
    void appointmentBooked_routesToAppointmentsTopic() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of(
                "appointmentId", "APT-001",
                "confirmationNumber", "CNF-001",
                "containerNumber", "MSKU123"
        ));
        DomainEvent<JsonNode> event = DomainEvent.of("gate.AppointmentBooked", "smartportx-gate", payload);

        router.handleEvent(event);

        ArgumentCaptor<NotificationPush> captor = ArgumentCaptor.forClass(NotificationPush.class);
        verify(broadcaster, times(1)).broadcast(captor.capture());

        assertThat(captor.getValue().topic()).isEqualTo("/topic/appointments/APT-001");
        assertThat(captor.getValue().title()).isEqualTo("Appointment Booked");
    }

    @Test
    void vesselArrived_routesToVesselTopic() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of(
                "vesselId", "V1",
                "vesselName", "Ever Given",
                "berthNumber", "B1"
        ));
        DomainEvent<JsonNode> event = DomainEvent.of("vessel.VesselArrived", "smartportx-vessel", payload);

        router.handleEvent(event);

        ArgumentCaptor<NotificationPush> captor = ArgumentCaptor.forClass(NotificationPush.class);
        verify(broadcaster, times(2)).broadcast(captor.capture());

        NotificationPush vesselPush = captor.getAllValues().stream()
                .filter(p -> p.topic().equals("/topic/vessel/V1"))
                .findFirst()
                .orElseThrow();
        assertThat(vesselPush.topic()).isEqualTo("/topic/vessel/V1");
        assertThat(vesselPush.title()).isEqualTo("Vessel Arrived");
    }

    @Test
    void unknownEventType_isSkipped() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of("foo", "bar"));
        DomainEvent<JsonNode> event = DomainEvent.of("unknown.EventType", "smartportx", payload);

        router.handleEvent(event);

        verify(broadcaster, never()).broadcast(any());
        verify(alertEngine, never()).routeAlert(any(), any(), any());
    }

    @Test
    void nullPayload_isHandled() {
        DomainEvent<JsonNode> event = DomainEvent.of("container.ContainerRegistered", "smartportx-container", null);

        router.handleEvent(event);

        ArgumentCaptor<NotificationPush> captor = ArgumentCaptor.forClass(NotificationPush.class);
        verify(broadcaster, times(1)).broadcast(captor.capture());

        assertThat(captor.getValue().topic()).isEqualTo("/topic/containers/");
        assertThat(captor.getValue().message()).contains("-");
    }

    @Test
    void severityAtLeastWarning_triggersAlertEngine() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of(
                "code", "CG-01",
                "newStatus", "OUT_OF_SERVICE"
        ));
        DomainEvent<JsonNode> event = DomainEvent.of("yard.EquipmentStatusChanged", "smartportx-yard", payload);

        router.handleEvent(event);

        verify(alertEngine).routeAlert(eq("yard.EquipmentStatusChanged"), eq(payload), eq(NotificationSeverity.WARNING));
    }

    @Test
    void severityInfo_doesNotTriggerAlertEngine() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of(
                "containerNumber", "MSKU1234567"
        ));
        DomainEvent<JsonNode> event = DomainEvent.of("container.ContainerRegistered", "smartportx-container", payload);

        router.handleEvent(event);

        verify(alertEngine, never()).routeAlert(any(), any(), any());
    }

    @Test
    void dashboardSummary_routesToDashboardTopic() throws Exception {
        JsonNode payload = objectMapper.valueToTree(Map.of("summary", "ok"));
        DomainEvent<JsonNode> event = DomainEvent.of("yard.YardOptimizationRun", "smartportx-yard", payload);

        router.handleEvent(event);

        ArgumentCaptor<NotificationPush> captor = ArgumentCaptor.forClass(NotificationPush.class);
        verify(broadcaster, times(2)).broadcast(captor.capture());

        var pushes = captor.getAllValues();
        assertThat(pushes).anySatisfy(p -> assertThat(p.topic()).isEqualTo("/topic/dashboard/summary"));
        assertThat(pushes).anySatisfy(p -> assertThat(p.topic()).isEqualTo("/topic/yard-status"));
    }
}