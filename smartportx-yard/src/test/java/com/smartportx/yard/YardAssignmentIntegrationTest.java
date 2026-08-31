package com.smartportx.yard;

import com.smartportx.yard.domain.YardBlock;
import com.smartportx.yard.domain.YardTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class YardAssignmentIntegrationTest extends BaseYardIntegrationTest {

    private YardBlock block;
    private List<YardTier> tiers;

    @BeforeEach
    void setUp() {
        TestContainerCatalog.reset();
        block = block("B01");
        tiers = tierRepository.findAll().stream()
                .sorted(Comparator.comparingInt(YardTier::getTierNumber)
                        .thenComparing(t -> t.getRowId().toString()))
                .toList();
    }

    private YardTier tier(int index) {
        return tiers.get(index);
    }

    @Test
    void assign_persistsAndReturns200() throws Exception {
        YardTier t = tier(0);
        performPut(assignUrl(t), assignBody(TestContainerCatalog.standard()), YardTestTokens.yardManager())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OCCUPIED"))
                .andExpect(jsonPath("$.position")
                        .value(org.hamcrest.Matchers.matchesPattern("B01-B[0-9]+-R[0-9]+-T[0-9]+")))
                .andExpect(jsonPath("$.containerId").isNotEmpty());
    }

    @Test
    void assign_occupiedTier_409() throws Exception {
        YardTier t = tier(1);
        performPut(assignUrl(t), assignBody(TestContainerCatalog.standard()), YardTestTokens.yardManager())
                .andExpect(status().isOk());
        performPut(assignUrl(t), assignBody(TestContainerCatalog.standard()), YardTestTokens.yardManager())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));
    }

    @Test
    void assign_unknownContainer_404() throws Exception {
        performPut(assignUrl(tier(2)), assignBody(UUID.randomUUID()), YardTestTokens.yardManager())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void assign_dangerousContainerToNonDangerousBlock_422() throws Exception {
        performPut(assignUrl(tier(3)), assignBody(TestContainerCatalog.dangerous()), YardTestTokens.yardManager())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("dangerous_goods_not_allowed"));
    }

    @Test
    void assign_reeferToNonRefrigeratedBlock_422() throws Exception {
        performPut(assignUrl(tier(4)), assignBody(TestContainerCatalog.reefer()), YardTestTokens.yardManager())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("reefer_requires_refrigerated_block"));
    }

    @Test
    void assign_weightExceedsBlockLimit_422() throws Exception {
        performPut(assignUrl(tier(5)), assignBody(TestContainerCatalog.heavy()), YardTestTokens.yardManager())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("weight_limit_exceeded"));
    }

    @Test
    void assign_invalidContainerStatus_422() throws Exception {
        performPut(assignUrl(tier(6)), assignBody(TestContainerCatalog.dispatched()), YardTestTokens.yardManager())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("invalid_container_status"));
    }

    @Test
    void assign_unauthenticated_401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put(assignUrl(tier(7)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBodyJson(TestContainerCatalog.standard())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void assign_readerRole_403() throws Exception {
        performPut(assignUrl(tier(8)), assignBody(TestContainerCatalog.standard()), YardTestTokens.reader())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }

    @Test
    void remove_freesTier_200() throws Exception {
        YardTier t = tier(9);
        UUID containerId = TestContainerCatalog.standard();
        performPut(assignUrl(t), assignBody(containerId), YardTestTokens.yardManager())
                .andExpect(status().isOk());

        performDelete(assignUrl(t), YardTestTokens.yardManager())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removedContainerId").value(containerId.toString()));

        performGet(tierDetailUrl(t), YardTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EMPTY"));
    }

    @Test
    void remove_emptyTier_404() throws Exception {
        performDelete(assignUrl(tier(10)), YardTestTokens.yardManager())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void remove_stackedAbove_422() throws Exception {
        YardTier lower = tier(7);
        YardTier upper = tiers.stream()
                .filter(t -> t.getRowId().equals(lower.getRowId()) && t.getTierNumber() > lower.getTierNumber())
                .findFirst().orElseThrow();
        performPut(assignUrl(lower), assignBody(TestContainerCatalog.standard()), YardTestTokens.yardManager())
                .andExpect(status().isOk());
        performPut(assignUrl(upper), assignBody(TestContainerCatalog.standard()), YardTestTokens.yardManager())
                .andExpect(status().isOk());

        performDelete(assignUrl(lower), YardTestTokens.yardManager())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("stacked_containers_above"));
    }

    @Test
    void remove_readerRole_403() throws Exception {
        YardTier t = tier(0);
        performPut(assignUrl(t), assignBody(TestContainerCatalog.standard()), YardTestTokens.yardManager())
                .andExpect(status().isOk());
        performDelete(assignUrl(t), YardTestTokens.reader())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }

    private String assignUrl(YardTier t) {
        return tierDetailUrl(t) + "/container";
    }

    private String tierDetailUrl(YardTier t) {
        UUID bayId = rowRepository.findById(t.getRowId()).orElseThrow().getBayId();
        return "/api/v1/yard/blocks/" + block.getId() + "/bays/" + bayId + "/rows/" + t.getRowId()
                + "/tiers/" + t.getId();
    }

    private String assignBody(UUID containerId) throws Exception {
        return assignBodyJson(containerId);
    }

    private String assignBodyJson(UUID containerId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("containerId", containerId));
    }
}