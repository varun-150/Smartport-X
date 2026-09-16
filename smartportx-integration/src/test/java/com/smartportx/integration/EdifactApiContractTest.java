package com.smartportx.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Provider-side contract test locking the API-CONTRACTS &sect;8 parse response envelope.
 * The integration service is inbound-bound (receives EDIFACT/IoT/customs feeds) and issues
 * no outbound partner HTTP calls in this slice, so the contract surface under test is the
 * exposed REST schema (see PHASE-4-FINDINGS M8-decision for that reconciliation).
 */
@Transactional
class EdifactApiContractTest extends BaseIntegrationTest {

    @Test
    void parseResponseEnvelopeMatchesApiContracts() throws Exception {
        String body = json(Map.of("messageType", "COPRAR",
                "rawMessage", coprar("IF9999", "COPRAR01", "MSKU1234567")));

        String response = doPost("/api/v1/integration/edifact/parse", body, IntegrationTestTokens.admin())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = parse(response);
        assertThat(root.has("correlationId")).isTrue();
        assertThat(root.get("messageType").asText()).isEqualTo("COPRAR");
        assertThat(root.get("messageId").asText()).isNotBlank();

        JsonNode parsedData = root.get("parsedData");
        assertThat(parsedData).isNotNull();
        assertThat(parsedData.get("messageReference").asText()).isEqualTo("COPRAR01");
        assertThat(parsedData.has("messageDate")).isTrue();
        assertThat(parsedData.has("messageTime")).isTrue();
        assertThat(parsedData.get("carrier")).isNotNull();
        assertThat(parsedData.get("carrier").has("name")).isTrue();
        assertThat(parsedData.get("carrier").has("code")).isTrue();
        assertThat(parsedData.get("carrier").has("country")).isTrue();
        assertThat(parsedData.get("vessel").has("name")).isTrue();
        assertThat(parsedData.get("vessel").has("imo")).isTrue();
        assertThat(parsedData.get("containers").isArray()).isTrue();
    }

    @Test
    void errorEnvelopeIsRfc9457() throws Exception {
        String response = doPost("/api/v1/integration/edifact/parse",
                json(Map.of("messageType", "COPRAR", "rawMessage", "garbage")),
                IntegrationTestTokens.admin())
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        JsonNode error = parse(response);
        Map<String, String> keys = new HashMap<>();
        error.fieldNames().forEachRemaining(n -> keys.put(n, n));
        assertThat(keys).containsKeys("type", "title", "status", "detail", "code");
    }
}
