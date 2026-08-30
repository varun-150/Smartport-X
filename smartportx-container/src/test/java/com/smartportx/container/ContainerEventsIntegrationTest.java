package com.smartportx.container;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartportx.common.event.DomainEvent;
import com.smartportx.container.domain.events.ContainerDomainEvents;
import com.smartportx.container.event.EventTransport;
import com.smartportx.container.web.dto.ContainerResponse;
import com.smartportx.container.web.dto.EventsPage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContainerEventsIntegrationTest extends BaseContainerIntegrationTest {

    @MockitoBean
    private EventTransport transport;

    @Test
    void register_publishesRegisteredEvent() throws Exception {
        ArgumentCaptor<DomainEvent<?>> captor = ArgumentCaptor.forClass(DomainEvent.class);
        ContainerResponse registered = registerContainer();

        verify(transport).publish(captor.capture());
        DomainEvent<?> event = captor.getValue();
        assertThat(event.eventType()).isEqualTo(ContainerDomainEvents.REGISTERED);
        assertThat(event.source()).isEqualTo("smartportx-container");
        JsonNode payload = (JsonNode) event.payload();
        assertThat(payload.get("containerNumber").asText()).isEqualTo(registered.containerNumber());
        assertThat(payload.get("status").asText()).isEqualTo("REGISTERED");
    }

    @Test
    void statusChange_publishesStatusChangedEvent() throws Exception {
        ContainerResponse registered = registerContainer();
        changeStatus(registered.id(), "AT_GATE");

        ArgumentCaptor<DomainEvent<?>> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(transport, atLeast(2)).publish(captor.capture());
        List<DomainEvent<?>> events = captor.getAllValues();
        DomainEvent<?> statusEvent = events.stream()
                .filter(e -> e.eventType().equals(ContainerDomainEvents.STATUS_CHANGED))
                .findFirst().orElseThrow();
        JsonNode payload = (JsonNode) statusEvent.payload();
        assertThat(payload.get("previousStatus").asText()).isEqualTo("REGISTERED");
        assertThat(payload.get("newStatus").asText()).isEqualTo("AT_GATE");
    }

    @Test
    void removal_publishesRemovedEvent() throws Exception {
        ContainerResponse registered = registerContainer();
        performDelete("/api/v1/containers/" + registered.id(), TestTokens.admin())
                .andExpect(status().isNoContent());

        ArgumentCaptor<DomainEvent<?>> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(transport, atLeast(2)).publish(captor.capture());
        List<DomainEvent<?>> events = captor.getAllValues();
        DomainEvent<?> removedEvent = events.stream()
                .filter(e -> e.eventType().equals(ContainerDomainEvents.REMOVED))
                .findFirst().orElseThrow();
        JsonNode payload = (JsonNode) removedEvent.payload();
        assertThat(payload.get("containerNumber").asText()).isEqualTo(registered.containerNumber());
        assertThat(payload.get("removedAt").asText()).isNotBlank();
    }

    @Test
    void eventsEndpoint_returnsLogTokens() throws Exception {
        ContainerResponse registered = registerContainer();
        changeStatus(registered.id(), "AT_GATE");

        EventsPage page = objectMapper.readValue(
                performGet("/api/v1/containers/" + registered.id() + "/events", TestTokens.reader())
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                EventsPage.class);

        List<String> tokens = page.data().stream()
                .map(e -> e.eventType()).toList();
        assertThat(tokens).contains(ContainerDomainEvents.LOG_REGISTERED,
                ContainerDomainEvents.LOG_STATUS_CHANGE);
        assertThat(page.data().get(0).containerNumber()).isEqualTo(registered.containerNumber());
    }
}