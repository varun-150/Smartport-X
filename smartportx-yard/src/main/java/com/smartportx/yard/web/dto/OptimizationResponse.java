package com.smartportx.yard.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OptimizationResponse(UUID optimizationId, UUID blockId, String strategy, boolean dryRun,
                                   Analysis analysis, List<Recommendation> recommendations,
                                   String status, Instant executedAt, Instant completedAt,
                                   Integer movesCompleted, Integer movesFailed,
                                   Double actualReshufflingReduction) {

    public record Analysis(int totalContainers, int currentReshuffles, int projectedReshuffles,
                           double estimatedReshufflingReduction, int containersAffected,
                           long estimatedExecutionTimeMs) {
    }

    public record Recommendation(int sequence, UUID containerId, String containerNumber,
                                 String currentPosition, String recommendedPosition, String reason,
                                 String moveType, String priority) {
    }

    public record Request(UUID blockId, String strategy, Boolean dryRun, Constraints constraints) {

        public record Constraints(Integer maxResufflesPerMove, Boolean preserveReeferPositions,
                                  Boolean avoidDangerousMixing) {
        }
    }

    public record HistoryEntry(UUID optimizationId, UUID blockId, String blockCode, String strategy,
                               String status, boolean dryRun, int containersAffected,
                               Double reshufflingReduction, String triggeredBy, Instant executedAt,
                               Instant completedAt) {
    }

    public record HistoriesResponse(List<HistoryEntry> data, PaginationResponse pagination) {
    }
}