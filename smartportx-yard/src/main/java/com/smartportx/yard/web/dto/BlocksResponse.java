package com.smartportx.yard.web.dto;

import java.util.List;
import java.util.UUID;

public record BlocksResponse(List<BlockSummaryResponse> data, Object pagination) {

    public static BlocksResponse of(List<BlockSummaryResponse> data) {
        return new BlocksResponse(data, null);
    }

    public record BlockSummaryResponse(UUID blockId, String blockCode, String name, String type,
                                       long totalSlots, long occupiedSlots, double utilizationRate,
                                       Double averageDwellDays, Boolean dangerousGoodsAllowed,
                                       long refrigeratedSlots) {
    }

    public record BayListResponse(List<BaySummaryResponse> data) {

        public record BaySummaryResponse(UUID bayId, int bayNumber, int totalRows, int totalTiers,
                                         long totalSlots, long occupiedSlots, double utilizationRate,
                                         Double maxStackWeight) {
        }
    }

    public record TierDetailResponse(UUID tierId, String position, String status,
                                     ContainerDetail container, Integer stackWeight, Double maxWeight,
                                     int stackCount, java.time.Instant lastModified) {

        public record ContainerDetail(UUID id, String number, String type, String owner, Double weight,
                                      String status, Object dangerousGoods, java.time.Instant receivedAt,
                                      java.time.Instant estimatedDeparture) {
        }
    }

    public record AssignContainerRequest(UUID containerId, String position) {
    }

    public record AssignResponse(UUID tierId, String position, String status, UUID containerId,
                                 String containerNumber, String containerType) {
    }

    public record RemoveContainerResponse(UUID removedContainerId, String position,
                                          java.time.Instant removedAt, String movedTo) {
    }

    public record UpdateBlockRequest(String name, String type, Boolean dangerousGoodsAllowed,
                                     Double maxStackWeight) {
    }
}