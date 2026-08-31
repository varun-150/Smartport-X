package com.smartportx.yard.web;

import com.smartportx.common.error.ApiException;
import com.smartportx.yard.domain.OptimizationHistory;
import com.smartportx.yard.domain.OptimizationStatus;
import com.smartportx.yard.domain.OptimizationStrategy;
import com.smartportx.yard.repository.OptimizationHistoryRepository;
import com.smartportx.yard.service.YardOptimizationService;
import com.smartportx.yard.web.dto.OptimizationResponse;
import com.smartportx.yard.web.dto.PaginationResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/yard/optimize")
public class YardOptimizationController {

    private final YardOptimizationService service;
    private final OptimizationHistoryRepository historyRepository;
    private final com.smartportx.yard.repository.YardBlockRepository blockRepository;

    public YardOptimizationController(YardOptimizationService service,
                                      OptimizationHistoryRepository historyRepository,
                                      com.smartportx.yard.repository.YardBlockRepository blockRepository) {
        this.service = service;
        this.historyRepository = historyRepository;
        this.blockRepository = blockRepository;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','YARD_MANAGER')")
    public OptimizationResponse optimize(@RequestBody OptimizationResponse.Request request) {
        if (request.blockId() == null) {
            throw new ApiException(400, "validation_failed", "blockId is required");
        }
        OptimizationStrategy strategy;
        try {
            strategy = OptimizationStrategy.valueOf(request.strategy());
        } catch (Exception e) {
            throw new ApiException(422, "invalid_strategy", "Unknown optimization strategy " + request.strategy());
        }
        boolean dryRun = request.dryRun() == null || request.dryRun();
        int maxResuffles = request.constraints() == null || request.constraints().maxResufflesPerMove() == null
                ? 5 : request.constraints().maxResufflesPerMove();
        YardOptimizationService.OptimizationRun run =
                service.optimize(request.blockId(), strategy, dryRun, maxResuffles);
        return new OptimizationResponse(run.optimizationId(), run.blockId(), run.strategy(), run.dryRun(),
                new OptimizationResponse.Analysis(run.recommendations().size(), run.currentReshuffles(),
                        run.projectedReshuffles(), run.estimatedReshufflingReduction(),
                        run.recommendations().size(), 0L),
                run.recommendations().stream()
                        .map(r -> new OptimizationResponse.Recommendation(r.sequence(), r.containerId(),
                                r.containerNumber(), r.currentPosition(), r.recommendedPosition(),
                                r.reason(), r.moveType(), r.priority()))
                        .toList(),
                run.status(), run.executedAt(), run.executedAt(), run.movesCompleted(), run.movesFailed(),
                run.actualReshufflingReduction());
    }

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public OptimizationResponse.HistoriesResponse history(@RequestParam(required = false) String cursor,
                                                          @RequestParam(defaultValue = "25") int limit,
                                                          @RequestParam(required = false) UUID blockId,
                                                          @RequestParam(required = false) String status) {
        int effectiveLimit = Math.max(1, Math.min(100, limit));
        PageRequest pageRequest = PageRequest.of(0, effectiveLimit,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        List<OptimizationHistory> histories;
        if (blockId != null && status != null) {
            histories = historyRepository.findByBlockIdAndStatusOrderByCreatedAtDesc(blockId, parseStatus(status), pageRequest);
        } else if (blockId != null) {
            histories = historyRepository.findByBlockIdOrderByCreatedAtDesc(blockId, pageRequest);
        } else if (status != null) {
            histories = historyRepository.findByStatusOrderByCreatedAtDesc(parseStatus(status), pageRequest);
        } else {
            histories = historyRepository.findAll(pageRequest).getContent();
        }
        long total = blockId == null ? historyRepository.count()
                : historyRepository.countByBlockId(blockId);
        List<OptimizationResponse.HistoryEntry> data = new ArrayList<>();
        for (OptimizationHistory h : histories) {
            String blockCode = blockCodeOf(h.getBlockId());
            data.add(new OptimizationResponse.HistoryEntry(h.getId(), h.getBlockId(), blockCode,
                    h.getStrategy() == null ? null : h.getStrategy().name(),
                    h.getStatus() == null ? null : h.getStatus().name(), h.isDryRun(),
                    h.getContainerCount(), h.getEstimatedReshufflingReduction(), h.getTriggeredBy(),
                    h.getCompletedAt(), h.getCompletedAt()));
        }
        boolean hasMore = histories.size() == effectiveLimit;
        return new OptimizationResponse.HistoriesResponse(data,
                new PaginationResponse(hasMore ? "next" : null, hasMore, total, effectiveLimit));
    }

    private OptimizationStatus parseStatus(String status) {
        try {
            return OptimizationStatus.valueOf(status);
        } catch (Exception e) {
            throw new ApiException(422, "invalid_status", "Unknown optimization status " + status);
        }
    }

    private String blockCodeOf(UUID blockId) {
        return blockRepository.findById(blockId).map(com.smartportx.yard.domain.YardBlock::getBlockCode)
                .orElse(null);
    }
}