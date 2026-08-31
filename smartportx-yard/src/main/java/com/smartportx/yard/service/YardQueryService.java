package com.smartportx.yard.service;

import com.smartportx.common.error.NotFoundException;
import com.smartportx.yard.domain.YardBay;
import com.smartportx.yard.domain.YardBlock;
import com.smartportx.yard.domain.YardRow;
import com.smartportx.yard.domain.YardTier;
import com.smartportx.yard.repository.YardBayRepository;
import com.smartportx.yard.repository.YardBlockRepository;
import com.smartportx.yard.repository.YardRowRepository;
import com.smartportx.yard.repository.YardTierRepository;
import com.smartportx.yard.util.YardPosition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class YardQueryService {

    private final YardBlockRepository blocks;
    private final YardBayRepository bays;
    private final YardRowRepository rows;
    private final YardTierRepository tiers;

    public YardQueryService(YardBlockRepository blocks, YardBayRepository bays,
                            YardRowRepository rows, YardTierRepository tiers) {
        this.blocks = blocks;
        this.bays = bays;
        this.rows = rows;
        this.tiers = tiers;
    }

    @Transactional(readOnly = true)
    public LayoutSnapshot layout(boolean includeEmpty, List<String> blockFilter) {
        List<YardBlock> selected = blockFilter == null || blockFilter.isEmpty()
                ? blocks.findAllByOrderByBlockCodeAsc()
                : blocks.findByBlockCodeIn(blockFilter);
        if (selected.isEmpty()) {
            return new LayoutSnapshot(List.of(), 0, 0, 0.0, Instant.now());
        }
        List<UUID> blockIds = selected.stream().map(YardBlock::getId).toList();
        Map<UUID, List<YardBay>> baysByBlock = groupBy(bays.findByBlockIdInOrderByBayNumberAsc(blockIds),
                YardBay::getBlockId);
        List<UUID> bayIds = baysByBlock.values().stream().flatMap(List::stream)
                .map(YardBay::getId).toList();
        Map<UUID, List<YardRow>> rowsByBay = groupBy(rows.findByBayIdInOrderByRowNumberAsc(bayIds),
                YardRow::getBayId);
        List<UUID> rowIds = rowsByBay.values().stream().flatMap(List::stream)
                .map(YardRow::getId).toList();
        Map<UUID, List<YardTier>> tiersByRow = groupBy(tiers.findByRowIdInOrderByTierNumberAsc(rowIds),
                YardTier::getRowId);

        List<BlockSnapshot> blockSnapshots = new ArrayList<>();
        long occupiedTotal = 0;
        long slotsTotal = 0;
        for (YardBlock block : selected) {
            BlockSnapshot bs = buildBlock(block, baysByBlock.getOrDefault(block.getId(), List.of()),
                    rowsByBay, tiersByRow, includeEmpty);
            blockSnapshots.add(bs);
            occupiedTotal += bs.occupiedSlots();
            slotsTotal += bs.totalSlots();
        }
        double utilization = slotsTotal == 0 ? 0.0 : ((double) occupiedTotal) / slotsTotal;
        return new LayoutSnapshot(blockSnapshots, slotsTotal, occupiedTotal, utilization, Instant.now());
    }

    @Transactional(readOnly = true)
    public BlockSnapshot blockDetail(UUID blockId) {
        YardBlock block = blocks.findById(blockId)
                .orElseThrow(() -> new NotFoundException("Block " + blockId + " not found"));
        List<YardBay> bayList = bays.findByBlockIdOrderByBayNumberAsc(blockId);
        List<UUID> bayIds = bayList.stream().map(YardBay::getId).toList();
        Map<UUID, List<YardRow>> rowsByBay = groupBy(rows.findByBayIdInOrderByRowNumberAsc(bayIds),
                YardRow::getBayId);
        List<UUID> rowIds = rowsByBay.values().stream().flatMap(List::stream)
                .map(YardRow::getId).toList();
        Map<UUID, List<YardTier>> tiersByRow = groupBy(tiers.findByRowIdInOrderByTierNumberAsc(rowIds),
                YardTier::getRowId);
        return buildBlock(block, bayList, rowsByBay, tiersByRow, true);
    }

    @Transactional(readOnly = true)
    public List<YardBay> bays(UUID blockId) {
        if (!blocks.existsById(blockId)) {
            throw new NotFoundException("Block " + blockId + " not found");
        }
        return bays.findByBlockIdOrderByBayNumberAsc(blockId);
    }

    @Transactional(readOnly = true)
    public TierContext tier(UUID blockId, UUID bayId, UUID rowId, UUID tierId) {
        YardBlock block = blocks.findById(blockId)
                .orElseThrow(() -> new NotFoundException("Block " + blockId + " not found"));
        YardBay bay = bays.findById(bayId)
                .filter(b -> b.getBlockId().equals(blockId))
                .orElseThrow(() -> new NotFoundException("Bay " + bayId + " not found"));
        YardRow row = rows.findById(rowId)
                .filter(r -> r.getBayId().equals(bayId))
                .orElseThrow(() -> new NotFoundException("Row " + rowId + " not found"));
        YardTier tier = tiers.findById(tierId)
                .filter(t -> t.getRowId().equals(rowId))
                .orElseThrow(() -> new NotFoundException("Tier " + tierId + " not found"));
        String position = YardPosition.format(block.getBlockCode(), bay.getBayNumber(),
                row.getRowNumber(), tier.getTierNumber());
        return new TierContext(block, bay, row, tier, position);
    }

    @Transactional(readOnly = true)
    public TierContext resolvePosition(String position) {
        YardPosition.Coordinates c = YardPosition.parse(position);
        YardBlock block = blockByCode(c.blockCode());
        YardBay bay = bays.findByBlockIdOrderByBayNumberAsc(block.getId()).stream()
                .filter(b -> b.getBayNumber() == c.bay())
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Bay " + c.bay() + " not found in block " + c.blockCode()));
        YardRow row = rows.findByBayIdOrderByRowNumberAsc(bay.getId()).stream()
                .filter(r -> r.getRowNumber() == c.row())
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Row " + c.row() + " not found in bay " + bay.getBayNumber()));
        YardTier tier = tiers.findByRowIdAndTierNumber(row.getId(), c.tier())
                .orElseThrow(() -> new NotFoundException("Tier " + c.tier() + " not found in row " + row.getRowNumber()));
        return new TierContext(block, bay, row, tier, position);
    }

    @Transactional(readOnly = true)
    public TierContext resolvePositionByTier(YardBlock block, YardTier tier) {
        YardRow row = rows.findById(tier.getRowId())
                .orElseThrow(() -> new NotFoundException("Row " + tier.getRowId() + " not found"));
        YardBay bay = bays.findById(row.getBayId())
                .orElseThrow(() -> new NotFoundException("Bay " + row.getBayId() + " not found"));
        String position = YardPosition.format(block.getBlockCode(), bay.getBayNumber(),
                row.getRowNumber(), tier.getTierNumber());
        return new TierContext(block, bay, row, tier, position);
    }

    @Transactional(readOnly = true)
    public List<YardBlock> allBlocks() {
        return blocks.findAllByOrderByBlockCodeAsc();
    }

    @Transactional(readOnly = true)
    public long countOccupied(UUID blockId) {
        List<YardBay> bayList = bays.findByBlockIdOrderByBayNumberAsc(blockId);
        List<UUID> bayIds = bayList.stream().map(YardBay::getId).toList();
        if (bayIds.isEmpty()) {
            return 0;
        }
        List<YardRow> rowList = rows.findByBayIdInOrderByRowNumberAsc(bayIds);
        List<UUID> rowIds = rowList.stream().map(YardRow::getId).toList();
        if (rowIds.isEmpty()) {
            return 0;
        }
        return tiers.countByRowIdInAndOccupiedTrue(rowIds);
    }

    @Transactional(readOnly = true)
    public long countSlots(UUID blockId) {
        return blocks.findById(blockId)
                .map(b -> (long) b.getTotalBays() * b.getTotalRows() * b.getTotalTiers())
                .orElse(0L);
    }

    @Transactional(readOnly = true)
    public long countOccupiedBays(UUID bayId) {
        List<YardRow> rowList = rows.findByBayIdOrderByRowNumberAsc(bayId);
        List<UUID> rowIds = rowList.stream().map(YardRow::getId).toList();
        if (rowIds.isEmpty()) {
            return 0;
        }
        return tiers.countByRowIdInAndOccupiedTrue(rowIds);
    }

    @Transactional(readOnly = true)
    public List<YardRow> rowsByBay(UUID bayId) {
        return rows.findByBayIdOrderByRowNumberAsc(bayId);
    }

    @Transactional(readOnly = true)
    public List<YardTier> column(YardTier tier, YardRow row) {
        return tiers.findByRowIdOrderByTierNumberAsc(row.getId()).stream()
                .sorted(Comparator.comparingInt(YardTier::getTierNumber))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<YardTier> blockTiers(UUID blockId) {
        List<YardBay> bayList = bays.findByBlockIdOrderByBayNumberAsc(blockId);
        List<UUID> bayIds = bayList.stream().map(YardBay::getId).toList();
        Map<UUID, List<YardRow>> rowsByBay = groupBy(rows.findByBayIdInOrderByRowNumberAsc(bayIds),
                YardRow::getBayId);
        List<UUID> rowIds = rowsByBay.values().stream().flatMap(List::stream)
                .map(YardRow::getId).toList();
        return tiers.findByRowIdInOrderByTierNumberAsc(rowIds);
    }

    @Transactional(readOnly = true)
    public List<YardTier> columnOf(UUID rowId) {
        return tiers.findByRowIdOrderByTierNumberAsc(rowId);
    }

    @Transactional(readOnly = true)
    public YardBlock block(UUID blockId) {
        return blocks.findById(blockId)
                .orElseThrow(() -> new NotFoundException("Block " + blockId + " not found"));
    }

    @Transactional(readOnly = true)
    public YardBlock blockByCode(String blockCode) {
        return blocks.findByBlockCode(blockCode)
                .orElseThrow(() -> new NotFoundException("Block " + blockCode + " not found"));
    }

    private BlockSnapshot buildBlock(YardBlock block, List<YardBay> bayList,
                                     Map<UUID, List<YardRow>> rowsByBay,
                                     Map<UUID, List<YardTier>> tiersByRow, boolean includeEmpty) {
        long occupied = 0;
        long total = block.totalSlots();
        List<BaySnapshot> baySnapshots = new ArrayList<>();
        for (YardBay bay : bayList) {
            List<YardRow> rowList = rowsByBay.getOrDefault(bay.getId(), List.of());
            List<RowSnapshot> rowSnapshots = new ArrayList<>();
            for (YardRow row : rowList) {
                List<YardTier> tierList = tiersByRow.getOrDefault(row.getId(), List.of());
                List<TierSnapshot> tierSnapshots = new ArrayList<>();
                for (YardTier tier : tierList) {
                    String position = YardPosition.format(block.getBlockCode(), bay.getBayNumber(),
                            row.getRowNumber(), tier.getTierNumber());
                    int stackWeight = columnWeight(tierList, tier, block);
                    tierSnapshots.add(TierSnapshot.of(tier, position, stackWeight, block.getMaxStackWeight()));
                }
                long rowOccupied = tierList.stream().filter(YardTier::isOccupied).count();
                occupied += rowOccupied;
                rowSnapshots.add(new RowSnapshot(row.getId(), row.getRowNumber(), tierSnapshots));
            }
            baySnapshots.add(new BaySnapshot(bay.getId(), bay.getBayNumber(), rowSnapshots));
        }
        if (!includeEmpty) {
            baySnapshots.forEach(bs -> bs.rows().forEach(rs ->
                    rs.tiers().removeIf(t -> !t.occupied())));
            baySnapshots.removeIf(bs -> bs.rows().stream()
                    .allMatch(rs -> rs.tiers().isEmpty()));
        }
        double utilization = total == 0 ? 0.0 : ((double) occupied) / total;
        return new BlockSnapshot(block, baySnapshots, occupied, utilization);
    }

    private static int columnWeight(List<YardTier> column, YardTier current, YardBlock block) {
        double weight = current.getWeightKg() == null ? 0.0 : current.getWeightKg();
        return (int) Math.round(weight);
    }

    private static <T, K> Map<K, List<T>> groupBy(List<T> items, Function<T, K> keyFn) {
        return items.stream().collect(Collectors.groupingBy(keyFn, LinkedHashMap::new, Collectors.toList()));
    }

    public record LayoutSnapshot(List<BlockSnapshot> blocks, long totalSlots, long occupiedSlots,
                                 double utilizationRate, Instant lastUpdated) {
    }

    public record BlockSnapshot(YardBlock block, List<BaySnapshot> bays, long occupiedSlots,
                                double utilizationRate) {

        public long totalSlots() {
            return block.totalSlots();
        }
    }

    public record BaySnapshot(UUID bayId, int bayNumber, List<RowSnapshot> rows) {
    }

    public record RowSnapshot(UUID rowId, int rowNumber, List<TierSnapshot> tiers) {
    }

    public record TierSnapshot(UUID tierId, int tierNumber, String position, boolean occupied,
                               UUID containerId, String containerType, Double weightKg,
                               int stackWeight, Double maxWeight) {

        static TierSnapshot of(YardTier tier, String position, int stackWeight, Double maxWeight) {
            return new TierSnapshot(tier.getId(), tier.getTierNumber(), position, tier.isOccupied(),
                    tier.getContainerId(), tier.getContainerType(), tier.getWeightKg(), stackWeight, maxWeight);
        }
    }

    public record TierContext(YardBlock block, YardBay bay, YardRow row, YardTier tier, String position) {
    }
}