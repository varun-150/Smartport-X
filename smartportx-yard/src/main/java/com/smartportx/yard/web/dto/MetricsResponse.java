package com.smartportx.yard.web.dto;

import com.smartportx.yard.service.YardMetricsService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MetricsResponse(String yardId, String period, Instant generatedAt,
                              long totalSlots, long occupiedSlots, long availableSlots,
                              double utilizationRate, List<UtilizationTrendPoint> utilizationTrend,
                              Double averageDwellTimeDays, List<DwellTimeDistribution> dwellTimeDistribution,
                              Double reshufflingRate, Integer totalMoves24h, Double averageMovesPerHour,
                              Integer containersReceived24h, Integer containersDispatched24h,
                              long dangerousGoodsCount, long refrigeratedCount, long emptyContainerCount) {

    public static MetricsResponse from(String period, YardMetricsService.YardMetrics m) {
        return new MetricsResponse(m.yardId(), period, m.generatedAt(), m.totalSlots(), m.occupiedSlots(),
                m.availableSlots(), m.utilizationRate(), m.utilizationTrend().stream()
                        .map(t -> new UtilizationTrendPoint(t.timestamp(), t.rate())).toList(),
                m.averageDwellTimeDays(), null, m.reshufflingRate(), m.totalMoves24h(),
                m.averageMovesPerHour(), m.containersReceived24h(), m.containersDispatched24h(),
                m.dangerousGoodsCount(), m.refrigeratedCount(), m.emptyContainerCount());
    }

    public record UtilizationTrendPoint(Instant timestamp, double rate) {
    }

    public record DwellTimeDistribution(String range, long count, double percentage) {
    }

    public record BlockMetricsList(List<BlockMetricsData> data) {

        public record BlockMetricsData(UUID blockId, String blockCode, String blockType, long totalSlots,
                                       long occupiedSlots, double utilizationRate, Double averageDwellDays,
                                       double reshufflingRate, long dangerousGoodsSlots,
                                       long refrigeratedSlots, long emptySlots) {
        }
    }
}