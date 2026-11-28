package com.smartportx.yard.service;

import com.smartportx.yard.domain.YardBay;
import com.smartportx.yard.domain.YardBlock;
import com.smartportx.yard.domain.YardMetric;
import com.smartportx.yard.domain.YardRow;
import com.smartportx.yard.domain.YardTier;
import com.smartportx.yard.repository.YardBlockRepository;
import com.smartportx.yard.repository.YardMetricRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class YardMetricsService {

    private final YardBlockRepository blocks;
    private final YardMetricRepository metricRepository;
    private final YardQueryService query;

    public YardMetricsService(YardBlockRepository blocks, YardMetricRepository metricRepository,
                              YardQueryService query) {
        this.blocks = blocks;
        this.metricRepository = metricRepository;
        this.query = query;
    }

    @Transactional(readOnly = true)
    public YardMetrics metrics(String period) {
        List<YardBlock> allBlocks = blocks.findAllByOrderByBlockCodeAsc();
        long totalSlots = 0;
        long occupiedSlots = 0;
        for (YardBlock block : allBlocks) {
            List<YardTier> blockTiers = query.blockTiers(block.getId());
            totalSlots += blockTiers.size();
            occupiedSlots += blockTiers.stream().filter(YardTier::isOccupied).count();
        }
        double utilization = totalSlots == 0 ? 0.0 : ((double) occupiedSlots) / totalSlots;

        List<YardMetric> recent = metricRepository.findByBlockIdInOrderByDateAsc(
                allBlocks.stream().map(YardBlock::getId).toList());
        List<TrendPoint> trend = recent.stream()
                .map(m -> new TrendPoint(m.getDate().atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
                        m.getUtilizationRate()))
                .toList();

        long reshufflingCount = 0;
        long dangerousGoods = 0;
        long refrigerated = 0;
        long empty = 0;
        for (YardBlock block : allBlocks) {
            List<YardTier> blockTiers = query.blockTiers(block.getId());
            reshufflingCount += countReshuffles(blockTiers);
            for (YardTier tier : blockTiers) {
                if (!tier.isOccupied()) {
                    continue;
                }
                String type = tier.getContainerType();
                if (type != null && type.endsWith("RF")) {
                    refrigerated++;
                } else if (block.isDangerousGoodsAllowed()) {
                    dangerousGoods++;
                } else if (block.getBlockType() != null
                        && block.getBlockType().name().equals("EMPTY")) {
                    empty++;
                }
            }
        }
        long totalContainers = occupiedSlots;

        return new YardMetrics("yard-001", period, Instant.now(), totalSlots, occupiedSlots,
                totalSlots - occupiedSlots, YardEventLogger.round4(utilization), trend, null, null,
                occupiedSlots == 0 ? 0.0 : YardEventLogger.round4((double) reshufflingCount / occupiedSlots),
                null, null, null, null, dangerousGoods, refrigerated, empty);
    }

    @Transactional(readOnly = true)
    public List<BlockMetrics> blockMetrics() {
        List<YardBlock> allBlocks = blocks.findAllByOrderByBlockCodeAsc();
        List<BlockMetrics> result = new ArrayList<>();
        for (YardBlock block : allBlocks) {
            List<YardTier> blockTiers = query.blockTiers(block.getId());
            long occupied = blockTiers.stream().filter(YardTier::isOccupied).count();
            long total = blockTiers.size();
            double utilization = total == 0 ? 0.0 : ((double) occupied) / total;
            long reshuffles = countReshuffles(blockTiers);
            long dangerousGoodsSlots = block.isDangerousGoodsAllowed()
                    ? blockTiers.stream().filter(YardTier::isOccupied).count() : 0;
            long refrigeratedSlots = block.getBlockType() != null
                    && block.getBlockType().name().equals("REFRIGERATED")
                    ? blockTiers.stream().filter(YardTier::isOccupied).count() : 0;
            long emptySlots = block.getBlockType() != null
                    && block.getBlockType().name().equals("EMPTY")
                    ? blockTiers.stream().filter(YardTier::isOccupied).count() : 0;
            result.add(new BlockMetrics(block.getId(), block.getBlockCode(),
                    block.getBlockType() == null ? null : block.getBlockType().name(), total, occupied,
                    YardEventLogger.round4(utilization), null,
                    occupied == 0 ? 0.0 : YardEventLogger.round4((double) reshuffles / occupied),
                    dangerousGoodsSlots, refrigeratedSlots, emptySlots));
        }
        return result;
    }

    private long countReshuffles(List<YardTier> blockTiers) {
        Map<UUID, List<YardTier>> byRow = new LinkedHashMap<>();
        for (YardTier tier : blockTiers) {
            byRow.computeIfAbsent(tier.getRowId(), k -> new ArrayList<>()).add(tier);
        }
        long count = 0;
        for (List<YardTier> column : byRow.values()) {
            boolean aboveOccupied = false;
            column.sort(Comparator.comparingInt(YardTier::getTierNumber));
            for (YardTier tier : column) {
                if (tier.isOccupied() && aboveOccupied) {
                    count++;
                }
                aboveOccupied = tier.isOccupied();
            }
        }
        return count;
    }

    public record TrendPoint(Instant timestamp, double rate) {
    }

    public record YardMetrics(String yardId, String period, Instant generatedAt, long totalSlots,
                              long occupiedSlots, long availableSlots, double utilizationRate,
                              List<TrendPoint> utilizationTrend, Double averageDwellTimeDays,
                              Object dwellTimeDistribution, Double reshufflingRate, Integer totalMoves24h,
                              Double averageMovesPerHour, Integer containersReceived24h,
                              Integer containersDispatched24h, long dangerousGoodsCount,
                              long refrigeratedCount, long emptyContainerCount) {
    }

    public record BlockMetrics(UUID blockId, String blockCode, String blockType, long totalSlots,
                               long occupiedSlots, double utilizationRate, Double averageDwellDays,
                               double reshufflingRate, long dangerousGoodsSlots, long refrigeratedSlots,
                               long emptySlots) {
    }
}