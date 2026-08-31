package com.smartportx.yard.web.dto;

import java.util.List;
import java.util.UUID;

public record BlockSummaryResponse(UUID blockId, String blockCode, String name, String type,
                                   long totalSlots, long occupiedSlots, double utilizationRate,
                                   Double averageDwellDays, boolean dangerousGoodsAllowed,
                                   long refrigeratedSlots) {

    public static BlockSummaryResponse of(String blockId, String blockCode, String name, String type,
                                          long totalSlots, long occupiedSlots, double utilizationRate,
                                          boolean dangerousGoodsAllowed, long refrigeratedSlots) {
        return new BlockSummaryResponse(UUID.fromString(blockId), blockCode, name, type,
                totalSlots, occupiedSlots, utilizationRate, null, dangerousGoodsAllowed, refrigeratedSlots);
    }

    public record BlocksResponse(List<BlockSummaryResponse> data, Object pagination) {
    }
}