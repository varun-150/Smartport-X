package com.smartportx.yard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "yard_blocks", schema = "smartportx_yard")
public class YardBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "block_code", nullable = false, length = 10, unique = true)
    private String blockCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "total_bays", nullable = false)
    private int totalBays;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "total_tiers", nullable = false)
    private int totalTiers;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", nullable = false, length = 20)
    private BlockType blockType;

    @Column(name = "location_x", nullable = false)
    private double locationX;

    @Column(name = "location_y", nullable = false)
    private double locationY;

    @Column(name = "dangerous_goods_allowed", nullable = false)
    private boolean dangerousGoodsAllowed;

    @Column(name = "max_stack_weight")
    private Double maxStackWeight;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getBlockCode() {
        return blockCode;
    }

    public void setBlockCode(String blockCode) {
        this.blockCode = blockCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getTotalBays() {
        return totalBays;
    }

    public void setTotalBays(int totalBays) {
        this.totalBays = totalBays;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getTotalTiers() {
        return totalTiers;
    }

    public void setTotalTiers(int totalTiers) {
        this.totalTiers = totalTiers;
    }

    public long totalSlots() {
        return (long) totalBays * totalRows * totalTiers;
    }

    public BlockType getBlockType() {
        return blockType;
    }

    public void setBlockType(BlockType blockType) {
        this.blockType = blockType;
    }

    public double getLocationX() {
        return locationX;
    }

    public void setLocationX(double locationX) {
        this.locationX = locationX;
    }

    public double getLocationY() {
        return locationY;
    }

    public void setLocationY(double locationY) {
        this.locationY = locationY;
    }

    public boolean isDangerousGoodsAllowed() {
        return dangerousGoodsAllowed;
    }

    public void setDangerousGoodsAllowed(boolean dangerousGoodsAllowed) {
        this.dangerousGoodsAllowed = dangerousGoodsAllowed;
    }

    public Double getMaxStackWeight() {
        return maxStackWeight;
    }

    public void setMaxStackWeight(Double maxStackWeight) {
        this.maxStackWeight = maxStackWeight;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}