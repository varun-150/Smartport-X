package com.smartportx.vessel;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartportx.vessel.domain.Vessel;
import com.smartportx.vessel.domain.VesselType;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class VesselRegistryIntegrationTest extends BaseVesselIntegrationTest {

    private static final String IMO = "IMO1234567";

    String newVesselBody() throws Exception {
        return json(new java.util.LinkedHashMap<>() {{
            put("imoNumber", IMO);
            put("vesselName", "Test Carrier");
            put("vesselType", "CONTAINER");
            put("lengthM", 300.0);
            put("beamM", 48.0);
            put("draftM", 14.0);
            put("teuCapacity", 12000);
            put("grossTonnage", 140000.0);
            put("flag", "PA");
            put("operator", "Test Lines");
            put("callSign", "TEST1");
        }});
    }

    @Test
    void registerVesselRequiresAdminOrTerminalOp() throws Exception {
        doPost("/api/v1/vessels", newVesselBody(), VesselTestTokens.reader())
                .andExpect(status().isForbidden());
        doPost("/api/v1/vessels", newVesselBody(), VesselTestTokens.readerWithPermission())
                .andExpect(status().isForbidden());

        doPost("/api/v1/vessels", newVesselBody(), VesselTestTokens.admin())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.startsWith("vsl-")))
                .andExpect(jsonPath("$.imoNumber").value(IMO));
    }

    @Test
    void registerRejectsDuplicateImo() throws Exception {
        doPost("/api/v1/vessels", newVesselBody(), VesselTestTokens.admin())
                .andExpect(status().isCreated());
        doPost("/api/v1/vessels", newVesselBody(), VesselTestTokens.admin())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("already_registered"));
    }

    @Test
    void registerRejectsMalformedImo() throws Exception {
        String bad = newVesselBody().replace("IMO1234567", "1234567");
        doPost("/api/v1/vessels", bad, VesselTestTokens.admin())
                .andExpect(status().isBadRequest());
    }

    @Test
    void getVesselByPrefixedOrBareId() throws Exception {
        JsonNode created = callJson(doPost("/api/v1/vessels", newVesselBody(), VesselTestTokens.admin()));
        String prefixed = created.get("id").asText();

        doGet("/api/v1/vessels/" + prefixed, VesselTestTokens.readerWithPermission())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vesselName").value("Test Carrier"));

        String bare = prefixed.substring("vsl-".length());
        doGet("/api/v1/vessels/" + bare, VesselTestTokens.readerWithPermission())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imoNumber").value(IMO));
    }

    @Test
    void listVesselsReturnsSeededFleet() throws Exception {
        doGet("/api/v1/vessels", VesselTestTokens.readerWithPermission())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void updateVesselChangesFields() throws Exception {
        JsonNode created = callJson(doPost("/api/v1/vessels", newVesselBody(), VesselTestTokens.admin()));
        String id = created.get("id").asText();
        String updated = newVesselBody().replace("Test Carrier", "Renamed Carrier");
        doPut("/api/v1/vessels/" + id, updated, VesselTestTokens.terminalOp())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vesselName").value("Renamed Carrier"));
    }

    @Test
    void deleteVesselRequiresAdmin() throws Exception {
        JsonNode created = callJson(doPost("/api/v1/vessels", newVesselBody(), VesselTestTokens.admin()));
        String id = created.get("id").asText();
        doDelete("/api/v1/vessels/" + id, VesselTestTokens.terminalOp())
                .andExpect(status().isForbidden());
        doDelete("/api/v1/vessels/" + id, VesselTestTokens.admin())
                .andExpect(status().isNoContent());
    }

    // ---- schedules ----

    String scheduleBody() throws Exception {
        return json(new java.util.LinkedHashMap<>() {{
            put("voyageNumber", "V-2026-101");
            put("berthNumber", "B02");
            put("eta", "2026-06-05T00:00:00Z");
            put("etd", "2026-06-06T00:00:00Z");
            put("priority", "HIGH");
            put("portOfCall", "PREV");
            put("nextPort", "NEXT");
            put("estimatedTeUs", 9000);
        }});
    }

    String makeVesselId() throws Exception {
        return callJson(doPost("/api/v1/vessels",
                json(new java.util.LinkedHashMap<>() {{
                    put("imoNumber", "IMO9999999");
                    put("vesselName", "Sched Carrier");
                    put("vesselType", "BULK");
                    put("lengthM", 200.0);
                    put("beamM", 32.0);
                    put("draftM", 12.0);
                }}), VesselTestTokens.admin())).get("id").asText();
    }

    @Test
    void addScheduleAndTransitionThroughLifecycle() throws Exception {
        String vesselId = makeVesselId();
        String body = scheduleBody();

        JsonNode schedule = callJson(doPost("/api/v1/vessels/" + vesselId + "/schedules", body,
                VesselTestTokens.vesselPlanner()));
        assertThat(schedule.get("id").asText()).startsWith("sch-");
        String scheduleId = schedule.get("id").asText();
        assertThat(schedule.get("status").asText()).isEqualTo("SCHEDULED");

        doPatch("/api/v1/schedules/" + scheduleId + "/status",
                json(new java.util.LinkedHashMap<>() {{ put("status", "ARRIVED"); }}),
                VesselTestTokens.terminalOp())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARRIVED"));

        assertThat(hasEvent("vessel.VesselArrived")).isTrue();
    }

    @Test
    void scheduleStartTransitionsSkippingArrivedAreAllowed() throws Exception {
        String vesselId = makeVesselId();
        JsonNode schedule = callJson(doPost("/api/v1/vessels/" + vesselId + "/schedules",
                scheduleBody(), VesselTestTokens.vesselPlanner()));
        String scheduleId = schedule.get("id").asText();

        // SCHEDULED -> IN_TRANSIT is valid
        doPatch("/api/v1/schedules/" + scheduleId + "/status",
                json(new java.util.LinkedHashMap<>() {{ put("status", "IN_TRANSIT"); }}),
                VesselTestTokens.terminalOp())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
    }

    @Test
    void terminalScheduleCannotChangeStatus() throws Exception {
        String vesselId = makeVesselId();
        JsonNode schedule = callJson(doPost("/api/v1/vessels/" + vesselId + "/schedules",
                scheduleBody(), VesselTestTokens.vesselPlanner()));
        String scheduleId = schedule.get("id").asText();

        doPatch("/api/v1/schedules/" + scheduleId + "/status",
                json(new java.util.LinkedHashMap<>() {{ put("status", "CANCELLED"); }}),
                VesselTestTokens.terminalOp())
                .andExpect(status().isOk());

        doPatch("/api/v1/schedules/" + scheduleId + "/status",
                json(new java.util.LinkedHashMap<>() {{ put("status", "ARRIVED"); }}),
                VesselTestTokens.terminalOp())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invalid_transition"));
    }

    @Test
    void inPortListsArrivedAndWorking() throws Exception {
        String vesselId = makeVesselId();
        JsonNode schedule = callJson(doPost("/api/v1/vessels/" + vesselId + "/schedules",
                scheduleBody(), VesselTestTokens.vesselPlanner()));
        String scheduleId = schedule.get("id").asText();
        doPatch("/api/v1/schedules/" + scheduleId + "/status",
                json(new java.util.LinkedHashMap<>() {{ put("status", "ARRIVED"); }}),
                VesselTestTokens.terminalOp()).andExpect(status().isOk());

        doGet("/api/v1/schedules/in-port", VesselTestTokens.readerWithPermission())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + scheduleId + "')]").exists());
    }

    @Test
    void scheduleRejectsEtdBeforeEta() throws Exception {
        String vesselId = makeVesselId();
        String bad = scheduleBody().replace("2026-06-06T00:00:00Z", "2026-06-04T00:00:00Z");
        doPost("/api/v1/vessels/" + vesselId + "/schedules", bad, VesselTestTokens.vesselPlanner())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("invalid_schedule"));
    }
}