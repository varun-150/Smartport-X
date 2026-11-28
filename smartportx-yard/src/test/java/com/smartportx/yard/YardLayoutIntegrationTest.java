package com.smartportx.yard;

import com.smartportx.yard.domain.YardBlock;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class YardLayoutIntegrationTest extends BaseYardIntegrationTest {

    @Test
    void layout_returnsStructure_200() throws Exception {
        performGet("/api/v1/yard/layout", YardTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks[0].blockCode").value("B01"))
                .andExpect(jsonPath("$.blocks[0].totalSlots").value(12))
                .andExpect(jsonPath("$.blocks[0].totalBays").value(2))
                .andExpect(jsonPath("$.blocks[0].bays[0].rows[0].tiers[0].position")
                        .value(org.hamcrest.Matchers.matchesPattern("B01-B[0-9]+-R[0-9]+-T[0-9]+")));
    }

    @Test
    void layout_unauthenticated_401() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/yard/layout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void blocks_returnsSummaries_200() throws Exception {
        performGet("/api/v1/yard/blocks", YardTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].blockCode").value("B01"))
                .andExpect(jsonPath("$.data[0].totalSlots").value(12))
                .andExpect(jsonPath("$.data[0].occupiedSlots").value(0));
    }

    @Test
    void blockDetail_200() throws Exception {
        YardBlock block = block("B01");
        performGet("/api/v1/yard/blocks/" + block.getId(), YardTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockId").value(block.getId().toString()))
                .andExpect(jsonPath("$.blockCode").value("B01"));
    }

    @Test
    void updateBlock_name_200() throws Exception {
        YardBlock block = block("B01");
        String body = objectMapper.writeValueAsString(java.util.Map.of("name", "Block 1 - Import Priority"));
        performPut("/api/v1/yard/blocks/" + block.getId(), body, YardTestTokens.yardManager())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Block 1 - Import Priority"));
    }

    @Test
    void updateBlock_invalidType_422() throws Exception {
        YardBlock block = block("B01");
        String body = objectMapper.writeValueAsString(java.util.Map.of("type", "NONSENSE"));
        performPut("/api/v1/yard/blocks/" + block.getId(), body, YardTestTokens.yardManager())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("invalid_block_type"));
    }

    @Test
    void bays_returnsSummaries_200() throws Exception {
        YardBlock block = block("B01");
        performGet("/api/v1/yard/blocks/" + block.getId() + "/bays", YardTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].bayNumber").value(1))
                .andExpect(jsonPath("$.data[0].totalSlots").value(6))
                .andExpect(jsonPath("$.data[0].totalRows").value(2));
    }

    @Test
    void tierDetail_empty_200() throws Exception {
        YardBlock block = block("B01");
        var bay = bayRepository.findByBlockIdOrderByBayNumberAsc(block.getId()).get(0);
        var row = rowRepository.findByBayIdOrderByRowNumberAsc(bay.getId()).get(0);
        var tier = tierRepository.findByRowIdOrderByTierNumberAsc(row.getId()).get(0);
        performGet("/api/v1/yard/blocks/" + block.getId() + "/bays/" + bay.getId() + "/rows/" + row.getId()
                + "/tiers/" + tier.getId(), YardTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EMPTY"))
                .andExpect(jsonPath("$.stackCount").value(3));
    }
}