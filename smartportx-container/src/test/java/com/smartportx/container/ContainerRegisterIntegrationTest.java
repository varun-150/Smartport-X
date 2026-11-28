package com.smartportx.container;

import com.smartportx.container.web.dto.ContainerResponse;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContainerRegisterIntegrationTest extends BaseContainerIntegrationTest {

    @Test
    void register_createsContainer_returns201() throws Exception {
        ContainerResponse container = registerContainer();

        assertThat(container.id()).isNotNull();
        assertThat(container.containerNumber()).matches("[A-Z]{4}[0-9]{7}");
        assertThat(container.containerType()).isEqualTo("40HC");
        assertThat(container.status()).hasToString("REGISTERED");
        assertThat(container.weight()).isEqualTo(25000.0);
    }

    @Test
    void register_rejectsInvalidIso6346_400() throws Exception {
        performPost("/api/v1/containers",
                objectMapper.writeValueAsString(registerPayload(TestContainerNumbers.badCheckDigit())),
                TestTokens.admin())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void register_rejectsMalformedNumber_400() throws Exception {
        performPost("/api/v1/containers",
                objectMapper.writeValueAsString(registerPayload("12345678901")),
                TestTokens.admin())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void register_duplicateNumber_409() throws Exception {
        String number = TestContainerNumbers.next();
        registerContainer(number);

        performPost("/api/v1/containers",
                objectMapper.writeValueAsString(registerPayload(number)),
                TestTokens.admin())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));
    }

    @Test
    void register_requiresAuthentication_401() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/containers")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerPayload(TestContainerNumbers.next()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void register_readerRole_403() throws Exception {
        performPost("/api/v1/containers",
                objectMapper.writeValueAsString(registerPayload(TestContainerNumbers.next())),
                TestTokens.reader())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }

    @Test
    void register_dangerousGoods_flattened() throws Exception {
        Map<String, Object> payload = registerPayload(TestContainerNumbers.next());
        Map<String, Object> dg = new HashMap<>();
        dg.put("unNumber", "UN1830");
        dg.put("properShippingName", "Sulfuric acid");
        dg.put("hazardClass", "8");
        dg.put("packingGroup", "II");
        dg.put("flashPoint", 0.0);
        dg.put("emergencyContact", "CHEMTREC");
        payload.put("dangerousGoods", dg);

        String body = objectMapper.writeValueAsString(payload);
        ContainerResponse container = objectMapper.readValue(
                performPost("/api/v1/containers", body, TestTokens.admin())
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                ContainerResponse.class);

        assertThat(container.dangerousGoods()).isTrue();
        assertThat(container.unNumber()).isEqualTo("UN1830");
        assertThat(container.properShippingName()).isEqualTo("Sulfuric acid");
        assertThat(container.hazardClass()).isEqualTo("8");
        assertThat(container.packingGroup()).isEqualTo("II");
    }

    @Test
    void update_partial_200() throws Exception {
        ContainerResponse registered = registerContainer();
        Map<String, Object> update = new HashMap<>();
        update.put("currentLocation", "BLK-A5");

        ContainerResponse updated = objectMapper.readValue(
                performPut("/api/v1/containers/" + registered.id(),
                        objectMapper.writeValueAsString(update), TestTokens.admin())
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                ContainerResponse.class);

        assertThat(updated.currentLocation()).isEqualTo("BLK-A5");
        assertThat(updated.owner()).isEqualTo("Maersk");
        assertThat(updated.containerNumber()).isEqualTo(registered.containerNumber());
    }

    @Test
    void update_unknownContainer_404() throws Exception {
        Map<String, Object> update = new HashMap<>();
        update.put("owner", "Hapag");
        performPut("/api/v1/containers/" + UUID.randomUUID(),
                objectMapper.writeValueAsString(update), TestTokens.admin())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }
}