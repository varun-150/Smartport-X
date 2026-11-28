package com.smartportx.yard.domain.events;

import java.time.Instant;
import java.util.UUID;

public final class YardDomainEvents {

    public static final String POSITION_ASSIGNED = "yard.YardPositionAssigned";
    public static final String POSITION_FREED = "yard.YardPositionFreed";
    public static final String OPTIMIZATION_RUN = "yard.YardOptimizationRun";
    public static final String EQUIPMENT_STATUS_CHANGED = "yard.EquipmentStatusChanged";

    private YardDomainEvents() {
    }

    public record PositionAssignedPayload(UUID containerId, String position, String blockCode,
                                          int bayNumber, int rowNumber, int tierNumber,
                                          Instant assignedAt) {
    }

    public record PositionFreedPayload(UUID containerId, String position, Instant freedAt) {
    }

    public record OptimizationRunPayload(String strategy, int containerCount,
                                         Double reshufflingReduction, UUID blockId, boolean dryRun) {
    }

    public record EquipmentStatusChangedPayload(UUID equipmentId, String code, String previousStatus,
                                                String newStatus, Instant changedAt) {
    }
}