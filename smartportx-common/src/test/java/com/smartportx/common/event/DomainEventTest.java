package com.smartportx.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventTest {

    record ContainerRegistered(String containerNumber, String owner) {
    }

    @Test
    void ofBuildsEnvelopeForSerialization() {
        DomainEvent<ContainerRegistered> event = DomainEvent.of("container.ContainerRegistered", "container", new ContainerRegistered("CN0000001", "ACME"));
        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.eventType()).isEqualTo("container.ContainerRegistered");
        assertThat(event.source()).isEqualTo("container");
        assertThat(event.correlationId()).isNotBlank();
    }

    @Test
    void jacksonRoundTripsEnvelopeAndPayload() throws Exception {
        DomainEvent<ContainerRegistered> event = DomainEvent.of("container.ContainerRegistered", "container", new ContainerRegistered("CN0000001", "ACME"));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(event);
        assertThat(json).contains("\"eventId\"").contains("\"containerNumber\":\"CN0000001\"");

        DomainEvent<ContainerRegistered> back = mapper.readValue(json, mapper.getTypeFactory()
                .constructParametricType(DomainEvent.class, ContainerRegistered.class));
        assertThat(back.eventId()).isEqualTo(event.eventId());
        assertThat(back.occurredAt()).isEqualTo(event.occurredAt());
        assertThat(back.correlationId()).isEqualTo(event.correlationId());
        assertThat(back.payload()).isEqualTo(event.payload());
        assertThat(back.payload().containerNumber()).isEqualTo("CN0000001");
    }
}