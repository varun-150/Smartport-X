package com.smartportx.yard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "optimization_history", schema = "smartportx_yard")
public class OptimizationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "block_id", nullable = false)
    private UUID blockId;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy", nullable = false, length = 30)
    private OptimizationStrategy strategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OptimizationStatus status;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Column(name = "container_count", nullable = false)
    private int containerCount;

    @Column(name = "estimated_reshuffling_reduction")
    private Double estimatedReshufflingReduction;

    @Column(name = "actual_reshuffling_reduction")
    private Double actualReshufflingReduction;

    @Column(name = "moves_completed")
    private Integer movesCompleted;

    @Column(name = "moves_failed")
    private Integer movesFailed;

    @Column(name = "execution_time_ms")
    private Integer executionTimeMs;

    @Column(name = "executed_by", nullable = false)
    private UUID executedBy;

    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getBlockId() {
        return blockId;
    }

    public void setBlockId(UUID blockId) {
        this.blockId = blockId;
    }

    public OptimizationStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(OptimizationStrategy strategy) {
        this.strategy = strategy;
    }

    public OptimizationStatus getStatus() {
        return status;
    }

    public void setStatus(OptimizationStatus status) {
        this.status = status;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public int getContainerCount() {
        return containerCount;
    }

    public void setContainerCount(int containerCount) {
        this.containerCount = containerCount;
    }

    public Double getEstimatedReshufflingReduction() {
        return estimatedReshufflingReduction;
    }

    public void setEstimatedReshufflingReduction(Double estimatedReshufflingReduction) {
        this.estimatedReshufflingReduction = estimatedReshufflingReduction;
    }

    public Double getActualReshufflingReduction() {
        return actualReshufflingReduction;
    }

    public void setActualReshufflingReduction(Double actualReshufflingReduction) {
        this.actualReshufflingReduction = actualReshufflingReduction;
    }

    public Integer getMovesCompleted() {
        return movesCompleted;
    }

    public void setMovesCompleted(Integer movesCompleted) {
        this.movesCompleted = movesCompleted;
    }

    public Integer getMovesFailed() {
        return movesFailed;
    }

    public void setMovesFailed(Integer movesFailed) {
        this.movesFailed = movesFailed;
    }

    public Long getExecutionTimeMs() {
        return executionTimeMs == null ? null : executionTimeMs.longValue();
    }

    public void setExecutionTimeMs(Long executionTimeMs) {
        this.executionTimeMs = executionTimeMs == null ? null : executionTimeMs.intValue();
    }

    public UUID getExecutedBy() {
        return executedBy;
    }

    public void setExecutedBy(UUID executedBy) {
        this.executedBy = executedBy;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}