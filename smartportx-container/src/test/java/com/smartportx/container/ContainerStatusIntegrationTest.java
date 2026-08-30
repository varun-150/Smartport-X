package com.smartportx.container;

import com.smartportx.container.web.dto.ContainerResponse;
import com.smartportx.container.web.dto.StatusChangeResponse;
import com.smartportx.container.web.dto.StatusHistoryResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContainerStatusIntegrationTest extends BaseContainerIntegrationTest {

    @Test
    void statusChange_validTransition_200() throws Exception {
        ContainerResponse registered = registerContainer();

        StatusChangeResponse response = objectMapper.readValue(
                changeStatus(registered.id(), "AT_GATE").getResponse().getContentAsString(),
                StatusChangeResponse.class);

        assertThat(response.previousStatus()).hasToString("REGISTERED");
        assertThat(response.newStatus()).hasToString("AT_GATE");
        assertThat(response.containerId()).isEqualTo(registered.id());

        ContainerResponse fetched = objectMapper.readValue(
                performGet("/api/v1/containers/" + registered.id(), TestTokens.reader())
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                ContainerResponse.class);
        assertThat(fetched.status()).hasToString("AT_GATE");
    }

    @Test
    void statusChange_sameStatus_422() throws Exception {
        ContainerResponse registered = registerContainer();

        changeStatus(registered.id(), "REGISTERED", TestTokens.admin())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("invalid_status_transition"));
    }

    @Test
    void statusChange_invalidTransition_422() throws Exception {
        ContainerResponse registered = registerContainer();

        changeStatus(registered.id(), "LOADED", TestTokens.admin())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("invalid_status_transition"));
    }

    @Test
    void statusChange_unknownContainer_404() throws Exception {
        changeStatus(UUID.randomUUID(), "AT_GATE", TestTokens.admin())
                .andExpect(status().isNotFound());
    }

    @Test
    void statusChange_readerRole_403() throws Exception {
        ContainerResponse registered = registerContainer();

        changeStatus(registered.id(), "AT_GATE", TestTokens.reader())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }

    @Test
    void statusChange_recordsHistory() throws Exception {
        ContainerResponse registered = registerContainer();
        changeStatus(registered.id(), "AT_GATE");

        List<StatusHistoryResponse> history = objectMapper.readValue(
                performGet("/api/v1/containers/" + registered.id() + "/status-history", TestTokens.reader())
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                new TypeReference<>() {
                });

        assertThat(history).isNotEmpty();
        assertThat(history.get(0).containerNumber()).isEqualTo(registered.containerNumber());
        assertThat(history.get(0).previousStatus()).hasToString("REGISTERED");
        assertThat(history.get(0).newStatus()).hasToString("AT_GATE");
    }

    @Test
    void movements_doNotAutoTransition() throws Exception {
        ContainerResponse registered = registerContainer();
        java.util.Map<String, Object> movement = new java.util.HashMap<>();
        movement.put("movementType", "GATE_IN");
        movement.put("from", "RCV");
        movement.put("to", "YRDA1");
        movement.put("timestamp", "2026-08-29T10:00:00Z");

        performPost("/api/v1/containers/" + registered.id() + "/movements",
                objectMapper.writeValueAsString(movement), TestTokens.admin())
                .andExpect(status().isOk());

        ContainerResponse fetched = objectMapper.readValue(
                performGet("/api/v1/containers/" + registered.id(), TestTokens.reader())
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                ContainerResponse.class);
        assertThat(fetched.status()).hasToString("REGISTERED");
        assertThat(fetched.currentLocation()).isEqualTo("YRDA1");
    }
}