package com.smartportx.integration;

import com.smartportx.integration.domain.events.IntegrationDomainEvents;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CustomsIntegrationTest extends BaseIntegrationTest {

    private String submit(String container, String declaration) throws Exception {
        return json(Map.of("containerNumber", container, "declarationNumber", declaration));
    }

    private String review(String decision, String reason) throws Exception {
        return json(Map.of("decision", decision, "reason", reason == null ? "" : reason));
    }

    @Test
    void submitThenReviewClearedThenReleaseThenGateOutAllowed() throws Exception {
        String container = "MSKU1234567";
        doPost("/api/v1/integration/customs/status", submit(container, "DEC-00001"),
                IntegrationTestTokens.terminalOp())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        doPost("/api/v1/integration/customs/status/" + container + "/review", review("CLEARED", null),
                IntegrationTestTokens.admin())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLEARED"));

        assertThat(hasEvent(IntegrationDomainEvents.CUSTOMS_STATUS_CHANGED)).isTrue();

        doPost("/api/v1/integration/customs/status/" + container + "/release", null,
                IntegrationTestTokens.admin())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));

        doGet("/api/v1/integration/customs/gate-out/" + container + "/validate", IntegrationTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void heldContainerBlocksGateOut() throws Exception {
        String container = "CMAU0000425";
        doPost("/api/v1/integration/customs/status", submit(container, "DEC-00002"),
                IntegrationTestTokens.admin())
                .andExpect(status().isCreated());

        doPost("/api/v1/integration/customs/status/" + container + "/review", review("HELD", "Documentation missing"),
                IntegrationTestTokens.admin())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.reason").value("Documentation missing"));

        doGet("/api/v1/integration/customs/gate-out/" + container + "/validate", IntegrationTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false));
    }

    @Test
    void unknownContainerGateOutIs404() throws Exception {
        doGet("/api/v1/integration/customs/gate-out/ZZZZ9999999/validate", IntegrationTestTokens.reader())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void reviewWithInvalidDecisionIs400() throws Exception {
        doPost("/api/v1/integration/customs/status", submit("MSKU1234567", "DEC-00003"),
                IntegrationTestTokens.admin())
                .andExpect(status().isCreated());

        doPost("/api/v1/integration/customs/status/MSKU1234567/review", review("REJECT", null),
                IntegrationTestTokens.admin())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void releasingUnclearedContainerIsConflict() throws Exception {
        doPost("/api/v1/integration/customs/status", submit("MSKU1234567", "DEC-00004"),
                IntegrationTestTokens.admin())
                .andExpect(status().isCreated());

        doPost("/api/v1/integration/customs/status/MSKU1234567/release", null, IntegrationTestTokens.admin())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));
    }

    @Test
    void containerStatusEndpointIsReadableByAuthenticatedReader() throws Exception {
        doPost("/api/v1/integration/customs/status", submit("MSKU1234567", "DEC-00005"),
                IntegrationTestTokens.terminalOp())
                .andExpect(status().isCreated());

        doGet("/api/v1/integration/customs/status/MSKU1234567", IntegrationTestTokens.plain())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.containerNumber").value("MSKU1234567"));
    }

    @Test
    void customsSubmitRequiresWritePermission() throws Exception {
        doPost("/api/v1/integration/customs/status", submit("MSKU1234567", "DEC-00006"),
                IntegrationTestTokens.reader())
                .andExpect(status().isForbidden());
    }

    @Test
    void customsStateMachinePublishesEvents() throws Exception {
        doPost("/api/v1/integration/customs/status", submit("MSKU1234567", "DEC-00007"),
                IntegrationTestTokens.admin()).andExpect(status().isCreated());
        doPost("/api/v1/integration/customs/status/MSKU1234567/review", review("CLEARED", null),
                IntegrationTestTokens.admin()).andExpect(status().isOk());
        doPost("/api/v1/integration/customs/status/MSKU1234567/release", null,
                IntegrationTestTokens.admin()).andExpect(status().isOk());

        assertThat(capturedEvents())
                .anyMatch(e -> e.eventType().equals(IntegrationDomainEvents.CUSTOMS_STATUS_CHANGED)
                        && e.payload() instanceof IntegrationDomainEvents.CustomsChangedPayload ccp
                        && "RELEASED".equals(ccp.newStatus()));
    }
}
