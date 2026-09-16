package com.smartportx.vessel;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class DischargeIntegrationTest extends BaseVesselIntegrationTest {

    String registerBulkAndReturnId() throws Exception {
        return callJson(doPost("/api/v1/vessels",
                json(new java.util.LinkedHashMap<>() {{
                    put("imoNumber", "IMO5551111");
                    put("vesselName", "Discharge Carrier");
                    put("vesselType", "CONTAINER");
                    put("lengthM", 300.0);
                    put("beamM", 45.0);
                    put("draftM", 13.0);
                }}), VesselTestTokens.admin())).get("id").asText();
    }

    String createPlanBody() throws Exception {
        return json(new java.util.LinkedHashMap<>() {{
            put("voyageNumber", "V-DIS-1");
            put("containers", java.util.List.of(
                    new java.util.LinkedHashMap<>() {{
                        put("containerNumber", "MSKU1000001");
                        put("containerType", "DRY");
                        put("weightKg", 21000.0);
                        put("vesselPosition", "020206");
                        put("yardTarget", "A-01-02-01");
                        put("priority", "HIGH");
                    }},
                    new java.util.LinkedHashMap<>() {{
                        put("containerNumber", "MSKU1000002");
                        put("containerType", "DRY");
                        put("weightKg", 18000.0);
                        put("vesselPosition", "020208");
                    }}));
        }});
    }

    @Test
    void createPlanPersistsContainersAndEmitsEvent() throws Exception {
        String vesselId = registerBulkAndReturnId();
        stubContainerFound("MSKU1000001", "ON_VESSEL");
        stubContainerFound("MSKU1000002", "ON_VESSEL");

        JsonNode plan = callJson(doPost("/api/v1/vessels/" + vesselId + "/discharge-plans",
                createPlanBody(), VesselTestTokens.vesselPlanner()));

        assertThat(plan.get("id").asText()).startsWith("dp-");
        assertThat(plan.get("totalContainers").asInt()).isEqualTo(2);
        assertThat(plan.get("status").asText()).isEqualTo("PLANNED");
        assertThat(plan.get("containers").size()).isEqualTo(2);
        assertThat(hasEvent("vessel.DischargePlanCreated")).isTrue();
    }

    @Test
    void createPlanRequiresPlannerOrOperator() throws Exception {
        String vesselId = registerBulkAndReturnId();
        doPost("/api/v1/vessels/" + vesselId + "/discharge-plans", createPlanBody(),
                VesselTestTokens.readerWithPermission())
                .andExpect(status().isForbidden());
    }

    @Test
    void progressFlowDischargeThenStackTriggersYardAndEvents() throws Exception {
        String vesselId = registerBulkAndReturnId();
        stubContainerFound("MSKU1000001", "ON_VESSEL");
        stubContainerFound("MSKU1000002", "ON_VESSEL");
        JsonNode plan = callJson(doPost("/api/v1/vessels/" + vesselId + "/discharge-plans",
                createPlanBody(), VesselTestTokens.vesselPlanner()));
        String planId = plan.get("id").asText();

        doPatch("/api/v1/discharge-plans/" + planId + "/progress",
                json(new java.util.LinkedHashMap<>() {{
                    put("containerNumber", "MSKU1000001");
                    put("status", "DISCHARGED");
                    put("equipmentId", "QC-01");
                    put("operator", "A-SHIFT");
                }}), VesselTestTokens.terminalOp())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.containers[0].status").value(
                        org.hamcrest.Matchers.anyOf(
                                org.hamcrest.Matchers.is("DISCHARGED"),
                                org.hamcrest.Matchers.is("PLANNED"))));

        doPatch("/api/v1/discharge-plans/" + planId + "/progress",
                json(new java.util.LinkedHashMap<>() {{
                    put("containerNumber", "MSKU1000001");
                    put("status", "IN_TRANSIT");
                }}), VesselTestTokens.terminalOp())
                .andExpect(status().isOk());

        doPatch("/api/v1/discharge-plans/" + planId + "/progress",
                json(new java.util.LinkedHashMap<>() {{
                    put("containerNumber", "MSKU1000001");
                    put("status", "STACKED");
                    put("actualYardPosition", "A-01-02-03");
                }}), VesselTestTokens.terminalOp())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.containers[?(@.containerNumber=='MSKU1000001')].actualYardPosition")
                        .value(org.hamcrest.Matchers.contains("A-01-02-03")));

        verify(yardClient).placeContainer(eq("A"), eq("01"), eq("02"), eq("03"), eq("MSKU1000001"), any(java.util.UUID.class));
        assertThat(hasEvent("vessel.DischargeProgressUpdated")).isTrue();
    }

    @Test
    void stackingWithoutYardTargetIsAllowedButDoesNotCallYard() throws Exception {
        String vesselId = registerBulkAndReturnId();
        stubContainerFound("MSKU1000001", "ON_VESSEL");
        stubContainerFound("MSKU1000002", "ON_VESSEL");
        JsonNode plan = callJson(doPost("/api/v1/vessels/" + vesselId + "/discharge-plans",
                createPlanBody(), VesselTestTokens.vesselPlanner()));
        String planId = plan.get("id").asText();

        dischargeTransitStack(planId, "MSKU1000002", null);

        verifyNoInteractions(yardClient);
    }

    @Test
    void planCompletesWhenAllContainersDischarged() throws Exception {
        String vesselId = registerBulkAndReturnId();
        stubContainerFound("MSKU1000001", "ON_VESSEL");
        stubContainerFound("MSKU1000002", "ON_VESSEL");
        JsonNode plan = callJson(doPost("/api/v1/vessels/" + vesselId + "/discharge-plans",
                createPlanBody(), VesselTestTokens.vesselPlanner()));
        String planId = plan.get("id").asText();

        for (String cn : new String[]{"MSKU1000001", "MSKU1000002"}) {
            dischargeOnly(planId, cn);
        }

        JsonNode finalPlan = callJson(doGet("/api/v1/discharge-plans/" + planId,
                VesselTestTokens.readerWithPermission()));
        assertThat(finalPlan.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(finalPlan.get("progress").get("completionPercentage").asDouble()).isEqualTo(100.0);
    }

    @Test
    void invalidTransitionIsRejected() throws Exception {
        String vesselId = registerBulkAndReturnId();
        stubContainerFound("MSKU1000001", "ON_VESSEL");
        stubContainerFound("MSKU1000002", "ON_VESSEL");
        JsonNode plan = callJson(doPost("/api/v1/vessels/" + vesselId + "/discharge-plans",
                createPlanBody(), VesselTestTokens.vesselPlanner()));
        String planId = plan.get("id").asText();

        // PLANNED -> STACKED is not a legal single-step transition
        doPatch("/api/v1/discharge-plans/" + planId + "/progress",
                json(new java.util.LinkedHashMap<>() {{
                    put("containerNumber", "MSKU1000001");
                    put("status", "STACKED");
                    put("actualYardPosition", "A-01-02-01");
                }}), VesselTestTokens.terminalOp())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invalid_transition"));
    }

    @Test
    void unknownContainerOnPlanIsRejected() throws Exception {
        String vesselId = registerBulkAndReturnId();
        stubContainerFound("MSKU1000001", "ON_VESSEL");
        stubContainerFound("MSKU1000002", "ON_VESSEL");
        JsonNode plan = callJson(doPost("/api/v1/vessels/" + vesselId + "/discharge-plans",
                createPlanBody(), VesselTestTokens.vesselPlanner()));
        String planId = plan.get("id").asText();

        doPatch("/api/v1/discharge-plans/" + planId + "/progress",
                json(new java.util.LinkedHashMap<>() {{
                    put("containerNumber", "NOTONPLAN123");
                    put("status", "DISCHARGED");
                }}), VesselTestTokens.terminalOp())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("container_not_on_plan"));
    }

    private void dischargeOnly(String planId, String containerNumber) throws Exception {
        doPatch("/api/v1/discharge-plans/" + planId + "/progress",
                json(new java.util.LinkedHashMap<>() {{
                    put("containerNumber", containerNumber);
                    put("status", "DISCHARGED");
                }}), VesselTestTokens.terminalOp()).andExpect(status().isOk());
    }

    private void dischargeTransitStack(String planId, String containerNumber, String yardPos) throws Exception {
        doPatch("/api/v1/discharge-plans/" + planId + "/progress",
                json(new java.util.LinkedHashMap<>() {{
                    put("containerNumber", containerNumber);
                    put("status", "DISCHARGED");
                }}), VesselTestTokens.terminalOp()).andExpect(status().isOk());
        doPatch("/api/v1/discharge-plans/" + planId + "/progress",
                json(new java.util.LinkedHashMap<>() {{
                    put("containerNumber", containerNumber);
                    put("status", "IN_TRANSIT");
                }}), VesselTestTokens.terminalOp()).andExpect(status().isOk());
        var m = new java.util.LinkedHashMap<String, Object>();
        m.put("containerNumber", containerNumber);
        m.put("status", "STACKED");
        if (yardPos != null) m.put("actualYardPosition", yardPos);
        doPatch("/api/v1/discharge-plans/" + planId + "/progress",
                json(m), VesselTestTokens.terminalOp()).andExpect(status().isOk());
    }
}