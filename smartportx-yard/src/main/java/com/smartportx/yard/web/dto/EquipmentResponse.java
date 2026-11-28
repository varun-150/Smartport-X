package com.smartportx.yard.web.dto;

import com.smartportx.yard.domain.EquipmentStatus;
import com.smartportx.yard.domain.EquipmentType;
import com.smartportx.yard.domain.YardEquipment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EquipmentResponse(UUID equipmentId, String code, String equipmentType,
                                UUID assignedBlockId, String status, int movesTotal,
                                Instant createdAt, Instant updatedAt) {

    public static EquipmentResponse from(YardEquipment e) {
        return new EquipmentResponse(e.getId(), e.getCode(),
                e.getEquipmentType() == null ? null : e.getEquipmentType().name(),
                e.getAssignedBlockId(), e.getStatus() == null ? null : e.getStatus().name(),
                e.getMovesTotal(), e.getCreatedAt(), e.getUpdatedAt());
    }

    public record ListResponse(List<EquipmentResponse> data) {
    }

    public record UpdateRequest(EquipmentStatus status, UUID assignedBlockId) {
    }

    public record MovesResponse(UUID equipmentId, String code, int movesTotal, Instant lastUpdated) {

        public static MovesResponse from(YardEquipment e) {
            return new MovesResponse(e.getId(), e.getCode(), e.getMovesTotal(), e.getUpdatedAt());
        }
    }
}