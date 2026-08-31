package com.smartportx.yard.service;

import com.smartportx.common.error.ApiException;
import com.smartportx.common.error.ConflictException;
import com.smartportx.common.error.NotFoundException;
import com.smartportx.yard.domain.BlockType;
import com.smartportx.yard.domain.YardBlock;
import com.smartportx.yard.domain.YardTier;
import com.smartportx.yard.repository.YardTierRepository;
import com.smartportx.yard.service.ContainerCatalog.ContainerInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class YardAssignmentService {

    private static final List<String> PLACEABLE_STATUSES = List.of("IN_YARD", "AT_GATE");

    private final YardQueryService query;
    private final YardTierRepository tiers;
    private final ContainerCatalog containerCatalog;
    private final YardEventLogger events;

    public YardAssignmentService(YardQueryService query, YardTierRepository tiers,
                                 ContainerCatalog containerCatalog, YardEventLogger events) {
        this.query = query;
        this.tiers = tiers;
        this.containerCatalog = containerCatalog;
        this.events = events;
    }

    @Transactional
    public AssignmentResult assign(UUID blockId, UUID bayId, UUID rowId, UUID tierId, UUID containerId,
                                   String requestedPosition) {
        YardQueryService.TierContext ctx = query.tier(blockId, bayId, rowId, tierId);
        YardTier tier = ctx.tier();

        if (tier.isOccupied()) {
            throw new ConflictException("Position " + ctx.position() + " is already occupied");
        }

        ContainerInfo container = containerCatalog.findById(containerId)
                .orElseThrow(() -> new NotFoundException("Container " + containerId + " not found"));

        validatePlaceable(container);
        validateBlockCompatibility(ctx.block(), container);

        YardBlock block = ctx.block();
        if (block.getMaxStackWeight() != null) {
            double columnWeight = columnWeightBelow(ctx, block);
            double incoming = container.weightKg() == null ? 0.0 : container.weightKg();
            if (columnWeight + incoming > block.getMaxStackWeight()) {
                throw new ApiException(422, "weight_limit_exceeded",
                        "Column weight " + (columnWeight + incoming) + "kg exceeds block max stack weight "
                                + block.getMaxStackWeight() + "kg");
            }
        }

        tier.setContainerId(container.id());
        tier.setContainerType(container.containerType());
        tier.setWeightKg(container.weightKg());
        tier.setOccupied(true);
        tiers.save(tier);

        events.positionAssigned(containerId, ctx.position(), block.getBlockCode(),
                ctx.bay().getBayNumber(), ctx.row().getRowNumber(), tier.getTierNumber());

        return new AssignmentResult(tier, ctx.position(), container);
    }

    @Transactional
    public ReleaseResult release(UUID blockId, UUID bayId, UUID rowId, UUID tierId) {
        YardQueryService.TierContext ctx = query.tier(blockId, bayId, rowId, tierId);
        YardTier tier = ctx.tier();
        if (!tier.isOccupied()) {
            throw new NotFoundException("Position " + ctx.position() + " is empty");
        }

        List<YardTier> column = query.columnOf(ctx.row().getId());
        boolean stackedAbove = column.stream()
                .filter(t -> t.getTierNumber() > tier.getTierNumber())
                .anyMatch(YardTier::isOccupied);
        if (stackedAbove) {
            throw new ApiException(422, "stacked_containers_above",
                    "Containers stacked above position " + ctx.position() + " must be removed first");
        }

        UUID removed = tier.getContainerId();
        tier.setContainerId(null);
        tier.setContainerType(null);
        tier.setWeightKg(null);
        tier.setOccupied(false);
        tiers.save(tier);

        events.positionFreed(removed, ctx.position());

        return new ReleaseResult(removed, ctx.position(), Instant.now());
    }

    @Transactional
    public AssignmentResult move(UUID blockId, UUID bayId, UUID rowId, UUID tierId, UUID fromBlockId,
                                 UUID fromBayId, UUID fromRowId, UUID fromTierId, UUID containerId,
                                 String requestedPosition) {
        release(fromBlockId, fromBayId, fromRowId, fromTierId);
        return assign(blockId, bayId, rowId, tierId, containerId, requestedPosition);
    }

    private void validatePlaceable(ContainerInfo container) {
        if (container.status() != null && !PLACEABLE_STATUSES.contains(container.status())) {
            throw new ApiException(422, "invalid_container_status",
                    "Container status " + container.status() + " is not valid for yard positioning");
        }
    }

    private void validateBlockCompatibility(YardBlock block, ContainerInfo container) {
        boolean dangerous = container.dangerousGoods();
        if (dangerous && !block.isDangerousGoodsAllowed()) {
            throw new ApiException(422, "dangerous_goods_not_allowed",
                    "Block " + block.getBlockCode() + " does not allow dangerous goods");
        }
        boolean reefer = container.containerType() != null
                && container.containerType().toUpperCase().endsWith("RF");
        if (reefer && block.getBlockType() != BlockType.REFRIGERATED) {
            throw new ApiException(422, "reefer_requires_refrigerated_block",
                    "Reefer container " + container.containerType() + " requires a refrigerated block");
        }
    }

    private double columnWeightBelow(YardQueryService.TierContext ctx, YardBlock block) {
        double total = 0.0;
        for (YardTier below : query.columnOf(ctx.row().getId())) {
            if (below.getTierNumber() >= ctx.tier().getTierNumber()) {
                continue;
            }
            if (below.isOccupied() && below.getWeightKg() != null) {
                total += below.getWeightKg();
            }
        }
        return total;
    }

    public record AssignmentResult(YardTier tier, String position, ContainerInfo container) {
    }

    public record ReleaseResult(UUID removedContainerId, String position, Instant removedAt) {
    }

    public record PositionScore(String position, int occupiedAbove, double weight, UUID tierId) {
    }
}