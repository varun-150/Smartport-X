package com.smartportx.container;

import com.smartportx.container.web.dto.ContainerResponse;
import com.smartportx.container.web.dto.MovementResponse;
import com.smartportx.container.web.dto.MovementsPage;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContainerMovementIntegrationTest extends BaseContainerIntegrationTest {

    @Test
    void recordMovement_createsMovement_200() throws Exception {
        ContainerResponse registered = registerContainer();
        Map<String, Object> movement = new HashMap<>();
        movement.put("movementType", "GATE_IN");
        movement.put("from", "RCV");
        movement.put("to", "YRDA1");
        movement.put("timestamp", "2026-08-29T10:00:00Z");
        movement.put("reason", "gate in");

        MovementResponse response = objectMapper.readValue(
                performPost("/api/v1/containers/" + registered.id() + "/movements",
                        objectMapper.writeValueAsString(movement), TestTokens.terminalOp())
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                MovementResponse.class);

        assertThat(response.movementId()).isNotNull();
        assertThat(response.containerId()).isEqualTo(registered.id());
        assertThat(response.containerNumber()).isEqualTo(registered.containerNumber());
        assertThat(response.movementType()).isEqualTo("GATE_IN");
        assertThat(response.from()).isEqualTo("RCV");
        assertThat(response.to()).isEqualTo("YRDA1");
        assertThat(response.operator()).isEqualTo("op-user");
    }

    @Test
    void recordMovement_readerRole_403() throws Exception {
        ContainerResponse registered = registerContainer();
        Map<String, Object> movement = new HashMap<>();
        movement.put("movementType", "GATE_IN");
        movement.put("timestamp", "2026-08-29T10:00:00Z");

        performPost("/api/v1/containers/" + registered.id() + "/movements",
                objectMapper.writeValueAsString(movement), TestTokens.reader())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }

    @Test
    void recordMovement_unknownContainer_404() throws Exception {
        Map<String, Object> movement = new HashMap<>();
        movement.put("movementType", "GATE_IN");
        movement.put("timestamp", "2026-08-29T10:00:00Z");

        performPost("/api/v1/containers/" + UUID.randomUUID() + "/movements",
                objectMapper.writeValueAsString(movement), TestTokens.admin())
                .andExpect(status().isNotFound());
    }

    @Test
    void movements_list_filterByType() throws Exception {
        ContainerResponse registered = registerContainer();
        Map<String, Object> gateIn = new HashMap<>();
        gateIn.put("movementType", "GATE_IN");
        gateIn.put("timestamp", "2026-08-29T10:00:00Z");
        Map<String, Object> yardMove = new HashMap<>();
        yardMove.put("movementType", "YARD_MOVE");
        yardMove.put("timestamp", "2026-08-29T11:00:00Z");
        performPost("/api/v1/containers/" + registered.id() + "/movements",
                objectMapper.writeValueAsString(gateIn), TestTokens.admin()).andExpect(status().isOk());
        performPost("/api/v1/containers/" + registered.id() + "/movements",
                objectMapper.writeValueAsString(yardMove), TestTokens.admin()).andExpect(status().isOk());

        MovementsPage page = objectMapper.readValue(
                performGet("/api/v1/containers/" + registered.id() + "/movements?filter[type]=YARD_MOVE",
                        TestTokens.reader())
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                MovementsPage.class);

        assertThat(page.data()).hasSize(1);
        assertThat(page.data().get(0).movementType()).isEqualTo("YARD_MOVE");
    }

    @Test
    void movements_list_unknownContainer_404() throws Exception {
        performGet("/api/v1/containers/" + UUID.randomUUID() + "/movements", TestTokens.reader())
                .andExpect(status().isNotFound());
    }
}