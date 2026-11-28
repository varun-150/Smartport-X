package com.smartportx.yard.web.dto;

import com.smartportx.yard.service.YardQueryService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LayoutResponse(String yardId, String yardName, long totalBlocks, long totalSlots,
                             long occupiedSlots, double utilizationRate, Instant lastUpdated,
                             List<Block> blocks) {

    public static LayoutResponse from(YardQueryService.LayoutSnapshot snapshot) {
        List<Block> blocks = snapshot.blocks().stream().map(Block::from).toList();
        return new LayoutResponse("yard-001", "Main Terminal Yard", blocks.size(),
                snapshot.totalSlots(), snapshot.occupiedSlots(), snapshot.utilizationRate(),
                snapshot.lastUpdated(), blocks);
    }

    public record Block(UUID blockId, String blockCode, String name, String type,
                        int totalBays, int totalRows, int totalTiers, long totalSlots,
                        long occupiedSlots, double utilizationRate, List<Bay> bays) {

        public static Block from(YardQueryService.BlockSnapshot b) {
            return new Block(b.block().getId(), b.block().getBlockCode(), b.block().getName(),
                    b.block().getBlockType() == null ? null : b.block().getBlockType().name(),
                    b.block().getTotalBays(), b.block().getTotalRows(), b.block().getTotalTiers(),
                    b.totalSlots(), b.occupiedSlots(), b.utilizationRate(),
                    b.bays().stream().map(Bay::from).toList());
        }
    }

    public record Bay(UUID bayId, int bayNumber, List<Row> rows) {

        public static Bay from(YardQueryService.BaySnapshot b) {
            return new Bay(b.bayId(), b.bayNumber(), b.rows().stream().map(Row::from).toList());
        }
    }

    public record Row(UUID rowId, int rowNumber, List<Tier> tiers) {

        public static Row from(YardQueryService.RowSnapshot r) {
            return new Row(r.rowId(), r.rowNumber(), r.tiers().stream().map(Tier::from).toList());
        }
    }

    public record Tier(UUID tierId, int tierNumber, String position, String status,
                       UUID containerId, String containerNumber, String containerType,
                       Integer stackWeight, Double maxWeight, Boolean dangerousGoods,
                       Instant estimatedDeparture) {

        public static Tier from(YardQueryService.TierSnapshot t) {
            return new Tier(t.tierId(), t.tierNumber(), t.position(),
                    t.occupied() ? "OCCUPIED" : "EMPTY", t.containerId(), null, t.containerType(),
                    t.occupied() ? Integer.valueOf(t.stackWeight()) : null, t.maxWeight(), null, null);
        }
    }
}