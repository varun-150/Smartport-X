package com.smartportx.vessel.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.List;

public final class DischargeDtos {

    private DischargeDtos() {
    }

    public record ListResponse<T>(List<T> items, String nextCursor) {
    }

    // ---- requests ----

    public record CreatePlanRequest(
            @NotBlank String voyageNumber,
            @NotEmpty List<ContainerPlanRequest> containers) {
    }

    public record ContainerPlanRequest(
            @NotBlank String containerNumber,
            @NotBlank String containerType,
            Double weightKg,
            @NotBlank String vesselPosition,
            String yardTarget,
            String priority,
            Boolean dangerousGoods,
            Boolean reefer) {
    }

    public record ProgressUpdateRequest(
            @NotBlank String containerNumber,
            @NotBlank String status,
            String actualYardPosition,
            Instant dischargedAt,
            Instant stackedAt,
            String equipmentId,
            String operator,
            String notes) {
    }

    // ---- responses ----

    public record PlanProgress(
            int totalContainers,
            int dischargedCount,
            int inTransitCount,
            int stackedCount,
            int pendingCount,
            double completionPercentage,
            Instant estimatedCompletion) {
    }

    public record PlanResponse(
            String id,
            String vesselId,
            String voyageNumber,
            String status,
            int totalContainers,
            int totalTeUs,
            int estimatedDurationMinutes,
            Instant createdAt,
            PlanProgress progress,
            List<DischargeContainerResponse> containers) {
    }

    public record DischargeContainerResponse(
            String containerNumber,
            String containerType,
            Double weightKg,
            String vesselPosition,
            String yardTarget,
            String status,
            String actualYardPosition,
            Instant dischargedAt,
            Instant stackedAt,
            String operator) {
    }

    public record ProgressResponse(
            List<DischargeContainerResponse> containers,
            PlanProgress progress) {
    }
}