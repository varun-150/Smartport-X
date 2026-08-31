package com.smartportx.yard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "yard_metrics", schema = "smartportx_yard")
public class YardMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "block_id", nullable = false)
    private UUID blockId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "total_slots", nullable = false)
    private int totalSlots;

    @Column(name = "occupied_slots", nullable = false)
    private int occupiedSlots;

    @Column(name = "utilization_rate", nullable = false)
    private double utilizationRate;

    @Column(name = "avg_dwell_time_hours")
    private Double avgDwellTimeHours;

    @Column(name = "reshuffling_count", nullable = false)
    private int reshufflingCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

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

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getTotalSlots() {
        return totalSlots;
    }

    public void setTotalSlots(int totalSlots) {
        this.totalSlots = totalSlots;
    }

    public int getOccupiedSlots() {
        return occupiedSlots;
    }

    public void setOccupiedSlots(int occupiedSlots) {
        this.occupiedSlots = occupiedSlots;
    }

    public double getUtilizationRate() {
        return utilizationRate;
    }

    public void setUtilizationRate(double utilizationRate) {
        this.utilizationRate = utilizationRate;
    }

    public Double getAvgDwellTimeHours() {
        return avgDwellTimeHours;
    }

    public void setAvgDwellTimeHours(Double avgDwellTimeHours) {
        this.avgDwellTimeHours = avgDwellTimeHours;
    }

    public int getReshufflingCount() {
        return reshufflingCount;
    }

    public void setReshufflingCount(int reshufflingCount) {
        this.reshufflingCount = reshufflingCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}