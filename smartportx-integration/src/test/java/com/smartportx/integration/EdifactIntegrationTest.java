package com.smartportx.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartportx.integration.domain.events.IntegrationDomainEvents;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class EdifactIntegrationTest extends BaseIntegrationTest {

    private String parseBody(String messageType, String raw) throws Exception {
        return json(Map.of("messageType", messageType, "rawMessage", raw));
    }

    @Test
    void coprarParsePersistsAndEmitsDomainEvents() throws Exception {
        String body = parseBody("COPRAR", coprar("IF12345", "COPRAR01", "MSKU1234567"));

        String response = doPost("/api/v1/integration/edifact/parse", body, IntegrationTestTokens.admin())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageType").value("COPRAR"))
                .andExpect(jsonPath("$.messageId").exists())
                .andExpect(jsonPath("$.parsedData.messageReference").value("COPRAR01"))
                .andExpect(jsonPath("$.parsedData.carrier.code").value("ZIMU"))
                .andExpect(jsonPath("$.parsedData.containers[0].containerNumber").value("MSKU1234567"))
                .andReturn().getResponse().getContentAsString();

        assertThat(edifactMessages.count()).isEqualTo(1);
        assertThat(hasEvent(IntegrationDomainEvents.EDIFACT_PARSED)).isTrue();
        assertThat(hasEvent(IntegrationDomainEvents.DISCHARGE_ORDER_RECEIVED)).isTrue();
        assertThat(payloadOfType(IntegrationDomainEvents.DISCHARGE_ORDER_RECEIVED,
                IntegrationDomainEvents.DischargeOrderPayload.class)).isPresent();

        JsonNode parsed = parse(response);
        String messageId = parsed.get("messageId").asText();
        doGet("/api/v1/integration/edifact/messages/" + messageId, IntegrationTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageType").value("COPRAR"));
    }

    @Test
    void coparnParseEmitsArrivalAnnouncedPerContainer() throws Exception {
        String body = parseBody("COPARN", coparn("IF88888", "CA01", "CMAU0000425"));

        doPost("/api/v1/integration/edifact/parse", body, IntegrationTestTokens.terminalOp())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageType").value("COPARN"));

        assertThat(hasEvent(IntegrationDomainEvents.ARRIVAL_ANNOUNCED)).isTrue();
        assertThat(payloadOfType(IntegrationDomainEvents.ARRIVAL_ANNOUNCED,
                IntegrationDomainEvents.ArrivalAnnouncementPayload.class))
                .isPresent()
                .get()
                .satisfies(p -> assertThat(p.containerNumber()).isEqualTo("CMAU0000425"));
    }

    @Test
    void duplicateReplayIsRejectedWith409() throws Exception {
        String body = parseBody("COPRAR", coprar("IF12345", "COPRAR01", "MSKU1234567"));
        String same = parseBody("COPRAR", coprar("IF12345", "COPRAR01", "MSKU1234567"));

        doPost("/api/v1/integration/edifact/parse", body, IntegrationTestTokens.admin())
                .andExpect(status().isCreated());
        doPost("/api/v1/integration/edifact/parse", same, IntegrationTestTokens.admin())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));

        assertThat(edifactMessages.count()).isEqualTo(1);
        long processed = edifactMessages.findAll().stream()
                .filter(m -> m.getStatus().name().equals("PROCESSED")).count();
        assertThat(processed).isEqualTo(1);
    }

    @Test
    void malformedMessageReturns400() throws Exception {
        String body = parseBody("COPRAR", "BGM+115+empty+9'");

        doPost("/api/v1/integration/edifact/parse", body, IntegrationTestTokens.admin())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void unsupportedMessageTypeReturns400() throws Exception {
        String body = parseBody("IFTMIN", "UNB+UNOC:3+A+B+1+260101:0000+1'");

        doPost("/api/v1/integration/edifact/parse", body, IntegrationTestTokens.admin())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("unsupported_message_type"));
    }

    @Test
    void unauthenticatedParseRejectedWith401() throws Exception {
        doPost("/api/v1/integration/edifact/parse",
                parseBody("COPRAR", coprar("IF12345", "COPRAR01", "MSKU1234567")), null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void plainUserCannotParseForbidden403() throws Exception {
        doPost("/api/v1/integration/edifact/parse",
                parseBody("COPRAR", coprar("IF12345", "COPRAR01", "MSKU1234567")),
                IntegrationTestTokens.plain())
                .andExpect(status().isForbidden());
    }

    @Test
    void contentTypeIsJson() throws Exception {
        doPost("/api/v1/integration/edifact/parse",
                parseBody("COPRAR", coprar("IF12345", "COPRAR01", "MSKU1234567")),
                IntegrationTestTokens.admin())
                .andExpect(status().isCreated());
        // ensure the parse endpoint still requires JSON content type on the request
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/integration/edifact/parse")
                        .header("Authorization", "Bearer " + IntegrationTestTokens.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
