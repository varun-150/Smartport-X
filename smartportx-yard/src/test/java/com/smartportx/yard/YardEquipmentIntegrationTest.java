package com.smartportx.yard;

import com.smartportx.yard.domain.YardEquipment;
import com.smartportx.yard.repository.YardEquipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class YardEquipmentIntegrationTest extends BaseYardIntegrationTest {

    @Autowired
    private YardEquipmentRepository equipmentRepository;

    private YardEquipment rtg;

    @BeforeEach
    void setUp() {
        rtg = equipmentRepository.findByCode("RTG-01").orElseThrow();
    }

    @Test
    void list_returnsEquipment_200() throws Exception {
        performGet("/api/v1/yard/equipment", YardTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("RTG-01"))
                .andExpect(jsonPath("$.data[0].status").value("ASSIGNED"))
                .andExpect(jsonPath("$.data[0].movesTotal").value(0));
    }

    @Test
    void list_filterOutOfService_returnsEmpty_200() throws Exception {
        performGet("/api/v1/yard/equipment?status=OUT_OF_SERVICE", YardTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void get_200() throws Exception {
        performGet("/api/v1/yard/equipment/" + rtg.getId(), YardTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("RTG-01"));
    }

    @Test
    void update_outOfService_200() throws Exception {
        performPut("/api/v1/yard/equipment/" + rtg.getId(),
                objectMapper.writeValueAsString(Map.of("status", "OUT_OF_SERVICE")),
                YardTestTokens.yardManager())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"))
                .andExpect(jsonPath("$.assignedBlockId").doesNotExist());
    }

    @Test
    void update_outOfServiceThenNoop_409() throws Exception {
        performPut("/api/v1/yard/equipment/" + rtg.getId(),
                objectMapper.writeValueAsString(Map.of("status", "OUT_OF_SERVICE")),
                YardTestTokens.yardManager()).andExpect(status().isOk());
        performPut("/api/v1/yard/equipment/" + rtg.getId(), "{}", YardTestTokens.yardManager())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("equipment_out_of_service"));
    }

    @Test
    void update_unknownEquipment_404() throws Exception {
        performPut("/api/v1/yard/equipment/" + UUID.randomUUID(),
                objectMapper.writeValueAsString(Map.of("status", "OUT_OF_SERVICE")),
                YardTestTokens.yardManager())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void update_reader_403() throws Exception {
        performPut("/api/v1/yard/equipment/" + rtg.getId(),
                objectMapper.writeValueAsString(Map.of("status", "OUT_OF_SERVICE")),
                YardTestTokens.reader())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }

    @Test
    void moves_200() throws Exception {
        performGet("/api/v1/yard/equipment/" + rtg.getId() + "/moves", YardTestTokens.reader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("RTG-01"))
                .andExpect(jsonPath("$.movesTotal").value(0));
    }
}