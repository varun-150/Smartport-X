package com.smartportx.yard;

import com.smartportx.yard.domain.YardBlock;
import com.smartportx.yard.domain.YardBay;
import com.smartportx.yard.domain.YardRow;
import com.smartportx.yard.domain.YardTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class YardOptimizationIntegrationTest extends BaseYardIntegrationTest {

    private YardBlock block;
    private UUID lowerId;
    private UUID upperId;

    @BeforeEach
    void setUp() throws Exception {
        TestContainerCatalog.reset();
        block = block("B01");
        YardBay bay = bayRepository.findByBlockIdOrderByBayNumberAsc(block.getId()).get(0);
        YardRow row = rowRepository.findByBayIdOrderByRowNumberAsc(bay.getId()).get(0);
        YardTier lower = tierRepository.findByRowIdOrderByTierNumberAsc(row.getId()).get(1);
        YardTier upper = tierRepository.findByRowIdOrderByTierNumberAsc(row.getId()).get(2);
        lowerId = lower.getId();
        upperId = upper.getId();
        UUID containerA = TestContainerCatalog.standard();
        UUID containerB = TestContainerCatalog.standard();
        place(containerA, lower);
        place(containerB, upper);
    }

    private void place(UUID containerId, YardTier tier) throws Exception {
        performPut(assignUrl(tier), objectMapper.writeValueAsString(Map.of("containerId", containerId)),
                YardTestTokens.yardManager()).andExpect(status().isOk());
    }

    private String assignUrl(YardTier t) {
        UUID bayId = rowRepository.findById(t.getRowId()).orElseThrow().getBayId();
        return "/api/v1/yard/blocks/" + block.getId() + "/bays/" + bayId + "/rows/" + t.getRowId()
                + "/tiers/" + t.getId() + "/container";
    }

    @Test
    void optimize_dryRun_returnsAnalysis_200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "blockId", block.getId().toString(),
                "strategy", "DEPARTURE_CLUSTER",
                "dryRun", true));
        performPost("/api/v1/yard/optimize", body, YardTestTokens.yardManager())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.optimizationId").isNotEmpty())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.analysis.currentReshuffles").value(1))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.recommendations[0].moveType").value("RESHUFFLE"))
                .andExpect(jsonPath("$.recommendations[0].recommendedPosition")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.is(org.hamcrest.Matchers.emptyOrNullString()))));
    }

    @Test
    void optimize_unknownStrategy_422() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "blockId", block.getId().toString(),
                "strategy", "CRYSTAL_BALL"));
        performPost("/api/v1/yard/optimize", body, YardTestTokens.yardManager())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("invalid_strategy"));
    }

    @Test
    void optimize_missingBlockId_400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("strategy", "FIFO"));
        performPost("/api/v1/yard/optimize", body, YardTestTokens.yardManager())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void optimize_reader_403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "blockId", block.getId().toString(),
                "strategy", "FIFO",
                "dryRun", true));
        performPost("/api/v1/yard/optimize", body, YardTestTokens.reader())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }

    @Test
    void history_returnsEnvelope_200() throws Exception {
        performPost("/api/v1/yard/optimize",
                objectMapper.writeValueAsString(Map.of("blockId", block.getId().toString(),
                        "strategy", "FIFO", "dryRun", true)),
                YardTestTokens.yardManager()).andExpect(status().isOk());

        performGet("/api/v1/yard/optimize/history", YardTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination").exists())
                .andExpect(jsonPath("$.data[0].blockId").value(block.getId().toString()))
                .andExpect(jsonPath("$.data[0].dryRun").value(true));
    }
}