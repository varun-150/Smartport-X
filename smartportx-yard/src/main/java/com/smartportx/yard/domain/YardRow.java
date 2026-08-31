package com.smartportx.yard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "yard_rows", schema = "smartportx_yard")
public class YardRow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bay_id", nullable = false)
    private UUID bayId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "tier_count", nullable = false)
    private int tierCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getBayId() {
        return bayId;
    }

    public void setBayId(UUID bayId) {
        this.bayId = bayId;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public int getTierCount() {
        return tierCount;
    }

    public void setTierCount(int tierCount) {
        this.tierCount = tierCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}