package com.smartportx.yard.service;

import com.smartportx.yard.domain.EquipmentStatus;
import com.smartportx.yard.domain.OptimizationHistory;
import com.smartportx.yard.domain.OptimizationStatus;
import com.smartportx.yard.domain.OptimizationStrategy;
import com.smartportx.yard.domain.YardBlock;
import com.smartportx.yard.domain.YardEquipment;
import com.smartportx.yard.domain.YardTier;
import com.smartportx.yard.repository.OptimizationHistoryRepository;
import com.smartportx.yard.repository.YardEquipmentRepository;
import com.smartportx.yard.service.ContainerCatalog.ContainerInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class YardOptimizationService {

    private final YardQueryService query;
    private final OptimizationHistoryRepository historyRepository;
    private final YardEquipmentRepository equipmentRepository;
    private final YardAssignmentService assignment;
    private final ContainerCatalog containerCatalog;
    private final YardEventLogger events;

    public YardOptimizationService(YardQueryService query, OptimizationHistoryRepository historyRepository,
                                   YardEquipmentRepository equipmentRepository, YardAssignmentService assignment,
                                   ContainerCatalog containerCatalog, YardEventLogger events) {
        this.query = query;
        this.historyRepository = historyRepository;
        this.equipmentRepository = equipmentRepository;
        this.assignment = assignment;
        this.containerCatalog = containerCatalog;
        this.events = events;
    }

    @Transactional
    public OptimizationRun optimize(UUID blockId, OptimizationStrategy strategy, boolean dryRun,
                                    int maxResufflesPerMove) {
        YardBlock block = query.block(blockId);
        List<YardTier> blockTiers = query.blockTiers(blockId);
        List<YardTier> occupied = blockTiers.stream().filter(YardTier::isOccupied).toList();

        int currentReshuffles = countReshuffles(blockTiers);
        int projectedReshuffles = estimateProjectedReshuffles(currentReshuffles, occupied.size(), strategy);
        double reduction = currentReshuffles == 0 ? 0.0
                : YardEventLogger.round4((double) (currentReshuffles - projectedReshuffles) / currentReshuffles);

        List<Recommendation> recommendations = buildRecommendations(block, blockTiers, strategy, maxResufflesPerMove);

        OptimizationHistory history = new OptimizationHistory();
        history.setBlockId(blockId);
        history.setStrategy(strategy);
        history.setStatus(OptimizationStatus.COMPLETED);
        history.setDryRun(dryRun);
        history.setContainerCount(occupied.size());
        history.setEstimatedReshufflingReduction(currentReshuffles == 0 ? null : reduction);
        history.setExecutedBy(UUID.randomUUID());
        history.setTriggeredBy("yard:optimize");

        if (dryRun) {
            history.setMovesCompleted(0);
            history.setMovesFailed(0);
            historyRepository.save(history);
        } else {
            executeMoves(block, recommendations, history);
        }

        events.optimizationRun(strategy.name(), occupied.size(), currentReshuffles == 0 ? null : reduction,
                blockId, dryRun);

        return new OptimizationRun(history.getId(), blockId, strategy.name(), dryRun, currentReshuffles,
                projectedReshuffles, reduction, recommendations, history.getStatus().name(),
                history.getCompletedAt(), history.getMovesCompleted(), history.getMovesFailed(),
                history.getActualReshufflingReduction());
    }

    private void executeMoves(YardBlock block, List<Recommendation> recommendations, OptimizationHistory history) {
        int completed = 0;
        int failed = 0;
        for (Recommendation r : recommendations) {
            try {
                YardQueryService.TierContext src = query.resolvePosition(r.currentPosition());
                YardQueryService.TierContext dst = query.resolvePosition(r.recommendedPosition());
                assignment.move(dst.block().getId(), dst.bay().getId(), dst.row().getId(), dst.tier().getId(),
                        src.block().getId(), src.bay().getId(), src.row().getId(), src.tier().getId(),
                        r.containerId(), dst.position());
                completed++;
            } catch (Exception e) {
                failed++;
            }
        }
        history.setMovesCompleted(completed);
        history.setMovesFailed(failed);
        history.setCompletedAt(Instant.now());
        history.setActualReshufflingReduction(completed == 0 ? null
                : YardEventLogger.round4((double) completed / Math.max(1, recommendations.size())));
        historyRepository.save(history);

        if (completed > 0) {
            YardEquipment equipment = equipmentRepository.findByAssignedBlockIdAndStatusIn(block.getId(),
                            List.of(EquipmentStatus.ASSIGNED, EquipmentStatus.AVAILABLE))
                    .stream().findFirst()
                    .orElseGet(() -> equipmentRepository.findByStatusOrderByCodeAsc(EquipmentStatus.AVAILABLE)
                            .stream().findFirst().orElse(null));
            if (equipment != null) {
                EquipmentStatus previous = equipment.getStatus();
                equipment.setMovesTotal(equipment.getMovesTotal() + completed);
                if (equipment.getAssignedBlockId() == null) {
                    equipment.setAssignedBlockId(block.getId());
                }
                if (equipment.getStatus() == EquipmentStatus.AVAILABLE) {
                    equipment.setStatus(EquipmentStatus.ASSIGNED);
                }
                equipmentRepository.save(equipment);
                if (previous != equipment.getStatus()) {
                    events.equipmentStatusChanged(equipment.getId(), equipment.getCode(), previous.name(),
                            equipment.getStatus().name());
                }
            }
        }
    }

    private int countReshuffles(List<YardTier> blockTiers) {
        int count = 0;
        for (List<YardTier> column : columnsByRow(blockTiers)) {
            boolean aboveOccupied = false;
            for (YardTier tier : column) {
                if (tier.isOccupied() && aboveOccupied) {
                    count++;
                }
                aboveOccupied = tier.isOccupied();
            }
        }
        return count;
    }

    private int estimateProjectedReshuffles(int current, int occupiedCount, OptimizationStrategy strategy) {
        if (current == 0 || occupiedCount == 0) {
            return 0;
        }
        double factor = switch (strategy) {
            case DEPARTURE_CLUSTER -> 0.40;
            case WEIGHT_CLUSTER -> 0.50;
            case FIFO -> 0.55;
            case BALANCED -> 0.45;
        };
        return Math.max(0, (int) Math.round(current * factor));
    }

    private List<Recommendation> buildRecommendations(YardBlock block, List<YardTier> blockTiers,
                                                      OptimizationStrategy strategy, int maxResufflesPerMove) {
        List<Recommendation> recommendations = new ArrayList<>();
        int sequence = 1;
        for (List<YardTier> column : columnsByRow(blockTiers)) {
            YardTier top = topOf(column);
            if (top == null || !top.isOccupied()) {
                continue;
            }
            List<YardTier> buriedBelow = column.stream()
                    .filter(YardTier::isOccupied)
                    .filter(t -> t.getTierNumber() < top.getTierNumber())
                    .toList();
            for (YardTier buried : buriedBelow) {
                if (recommendations.size() >= maxResufflesPerMove) {
                    break;
                }
                String target = targetPositionFor(block, blockTiers, column, buried);
                if (target == null) {
                    continue;
                }
                YardQueryService.TierContext ctx = query.resolvePositionByTier(block, buried);
                ContainerInfo container = containerCatalog.findById(buried.getContainerId()).orElse(null);
                String reason = reasonFor(strategy, buried, block);
                recommendations.add(new Recommendation(sequence++, buried.getContainerId(),
                        container == null ? null : container.containerNumber(),
                        ctx.position(), target, reason, "RESHUFFLE", priorityFor(buried)));
            }
            if (recommendations.size() >= maxResufflesPerMove) {
                break;
            }
        }
        return recommendations;
    }

    private List<List<YardTier>> columnsByRow(List<YardTier> blockTiers) {
        Map<UUID, List<YardTier>> byRow = new LinkedHashMap<>();
        for (YardTier tier : blockTiers) {
            byRow.computeIfAbsent(tier.getRowId(), k -> new ArrayList<>()).add(tier);
        }
        return byRow.values().stream()
                .map(col -> col.stream().sorted(Comparator.comparingInt(YardTier::getTierNumber)).toList())
                .toList();
    }

    private YardTier topOf(List<YardTier> column) {
        return column.isEmpty() ? null : column.get(column.size() - 1);
    }

    private String reasonFor(OptimizationStrategy strategy, YardTier tier, YardBlock block) {
        ContainerInfo container = containerCatalog.findById(tier.getContainerId()).orElse(null);
        String number = container == null ? String.valueOf(tier.getContainerId()) : container.containerNumber();
        return switch (strategy) {
            case FIFO -> "First-in-first-out — " + number + " is buried; move to top of stack for early access";
            case WEIGHT_CLUSTER -> "Weight cluster — " + number + " relocated to reduce weighted crane moves";
            case DEPARTURE_CLUSTER -> "Departure cluster — " + number + " grouped with same-departure stack";
            case BALANCED -> "Balanced — " + number + " moved to balance reshuffle and weight objectives";
        };
    }

    private String priorityFor(YardTier tier) {
        return "MEDIUM";
    }

    private String targetPositionFor(YardBlock block, List<YardTier> blockTiers, List<YardTier> column,
                                     YardTier buried) {
        YardTier target = column.stream()
                .filter(t -> !t.isOccupied())
                .filter(t -> !t.getId().equals(buried.getId()))
                .min(Comparator.comparingInt(YardTier::getTierNumber))
                .orElseGet(() -> blockTiers.stream()
                        .filter(t -> !t.isOccupied())
                        .filter(t -> !t.getRowId().equals(buried.getRowId()))
                        .min(Comparator.comparingInt(YardTier::getTierNumber))
                        .orElse(null));
        if (target == null) {
            return null;
        }
        return query.resolvePositionByTier(block, target).position();
    }

    public record Recommendation(int sequence, UUID containerId, String containerNumber,
                                 String currentPosition, String recommendedPosition, String reason,
                                 String moveType, String priority) {
    }

    public record OptimizationRun(UUID optimizationId, UUID blockId, String strategy, boolean dryRun,
                                  int currentReshuffles, int projectedReshuffles, double estimatedReshufflingReduction,
                                  List<Recommendation> recommendations, String status, Instant executedAt,
                                  Integer movesCompleted, Integer movesFailed, Double actualReshufflingReduction) {
    }
}