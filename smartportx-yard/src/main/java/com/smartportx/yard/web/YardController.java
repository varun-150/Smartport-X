package com.smartportx.yard.web;

import com.smartportx.common.error.ApiException;
import com.smartportx.yard.domain.BlockType;
import com.smartportx.yard.domain.YardBay;
import com.smartportx.yard.domain.YardBlock;
import com.smartportx.yard.domain.YardEquipment;
import com.smartportx.yard.domain.YardTier;
import com.smartportx.yard.repository.YardBlockRepository;
import com.smartportx.yard.service.YardAssignmentService;
import com.smartportx.yard.service.ContainerCatalog;
import com.smartportx.yard.service.YardQueryService;
import com.smartportx.yard.web.dto.BlocksResponse;
import com.smartportx.yard.web.dto.LayoutResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/yard")
public class YardController {

    private final YardQueryService query;
    private final YardAssignmentService assignment;
    private final ContainerCatalog containerCatalog;
    private final YardBlockRepository blockRepository;

    public YardController(YardQueryService query, YardAssignmentService assignment,
                          ContainerCatalog containerCatalog, YardBlockRepository blockRepository) {
        this.query = query;
        this.assignment = assignment;
        this.containerCatalog = containerCatalog;
        this.blockRepository = blockRepository;
    }

    @GetMapping("/layout")
    @PreAuthorize("isAuthenticated()")
    public LayoutResponse layout(@RequestParam(defaultValue = "true") boolean includeEmpty,
                                 @RequestParam(required = false) List<String> blockFilter) {
        return LayoutResponse.from(query.layout(includeEmpty, blockFilter));
    }

    @GetMapping("/blocks")
    @PreAuthorize("isAuthenticated()")
    public BlocksResponse blocks() {
        List<BlocksResponse.BlockSummaryResponse> data = new ArrayList<>();
        for (YardBlock block : query.allBlocks()) {
            long occupied = query.countOccupied(block.getId());
            long total = query.countSlots(block.getId());
            double utilization = total == 0 ? 0.0 : ((double) occupied) / total;
            data.add(new BlocksResponse.BlockSummaryResponse(block.getId(), block.getBlockCode(),
                    block.getName(), block.getBlockType() == null ? null : block.getBlockType().name(),
                    total, occupied, utilization, null,
                    block.isDangerousGoodsAllowed(), 0));
        }
        return BlocksResponse.of(data);
    }

    @GetMapping("/blocks/{blockId}")
    @PreAuthorize("isAuthenticated()")
    public Object blockDetail(@PathVariable UUID blockId) {
        return LayoutResponse.Block.from(query.blockDetail(blockId));
    }

    @PutMapping("/blocks/{blockId}")
    @PreAuthorize("hasAnyRole('ADMIN','YARD_MANAGER','TERMINAL_OP')")
    public BlocksResponse.BlockSummaryResponse updateBlock(@PathVariable UUID blockId,
                                                           @RequestBody BlocksResponse.UpdateBlockRequest request) {
        YardBlock block = blockRepository.findById(blockId)
                .orElseThrow(() -> new com.smartportx.common.error.NotFoundException("Block " + blockId + " not found"));
        if (request.name() != null) {
            block.setName(request.name());
        }
        if (request.type() != null) {
            try {
                block.setBlockType(BlockType.valueOf(request.type()));
            } catch (IllegalArgumentException e) {
                throw new ApiException(422, "invalid_block_type", "Unknown block type " + request.type());
            }
        }
        if (request.dangerousGoodsAllowed() != null) {
            block.setDangerousGoodsAllowed(request.dangerousGoodsAllowed());
        }
        if (request.maxStackWeight() != null) {
            block.setMaxStackWeight(request.maxStackWeight());
        }
        blockRepository.save(block);
        long occupied = query.countOccupied(blockId);
        long total = query.countSlots(blockId);
        return new BlocksResponse.BlockSummaryResponse(block.getId(), block.getBlockCode(), block.getName(),
                block.getBlockType() == null ? null : block.getBlockType().name(), total, occupied,
                total == 0 ? 0.0 : ((double) occupied) / total, null, block.isDangerousGoodsAllowed(), 0);
    }

    @GetMapping("/blocks/{blockId}/bays")
    @PreAuthorize("isAuthenticated()")
    public BlocksResponse.BayListResponse bays(@PathVariable UUID blockId) {
        List<YardBay> bayList = query.bays(blockId);
        List<BlocksResponse.BayListResponse.BaySummaryResponse> data = new ArrayList<>();
        for (YardBay bay : bayList) {
            long rows = bay.getRowCount();
            long tiers = bayTotalTiers(bay.getId());
            long total = rows * tiers;
            long occupied = query.countOccupiedBays(bay.getId());
            data.add(new BlocksResponse.BayListResponse.BaySummaryResponse(bay.getId(), bay.getBayNumber(),
                    bay.getRowCount(), (int) tiers, total, occupied,
                    total == 0 ? 0.0 : ((double) occupied) / total, blockMaxWeight(blockId)));
        }
        return new BlocksResponse.BayListResponse(data);
    }

    @GetMapping("/blocks/{blockId}/bays/{bayId}/rows/{rowId}/tiers/{tierId}")
    @PreAuthorize("isAuthenticated()")
    public BlocksResponse.TierDetailResponse tierDetail(@PathVariable UUID blockId, @PathVariable UUID bayId,
                                                        @PathVariable UUID rowId, @PathVariable UUID tierId) {
        YardQueryService.TierContext ctx = query.tier(blockId, bayId, rowId, tierId);
        YardTier tier = ctx.tier();
        Optional<ContainerCatalog.ContainerInfo> container = tier.isOccupied()
                ? containerCatalog.findById(tier.getContainerId()) : Optional.empty();
        BlocksResponse.TierDetailResponse.ContainerDetail containerDetail = container.map(c ->
                        new BlocksResponse.TierDetailResponse.ContainerDetail(c.id(), c.containerNumber(),
                                c.containerType(), null, c.weightKg(), c.status(), null, c.receivedAt(),
                                c.estimatedDeparture()))
                .orElse(null);
        return new BlocksResponse.TierDetailResponse(tier.getId(), ctx.position(),
                tier.isOccupied() ? "OCCUPIED" : "EMPTY", containerDetail, null, null,
                stackCount(ctx), Instant.now());
    }

    @PutMapping("/blocks/{blockId}/bays/{bayId}/rows/{rowId}/tiers/{tierId}/container")
    @PreAuthorize("hasAnyRole('ADMIN','YARD_MANAGER','TERMINAL_OP')")
    public BlocksResponse.AssignResponse assign(@PathVariable UUID blockId, @PathVariable UUID bayId,
                                                @PathVariable UUID rowId, @PathVariable UUID tierId,
                                                @RequestBody BlocksResponse.AssignContainerRequest body) {
        YardAssignmentService.AssignmentResult result =
                assignment.assign(blockId, bayId, rowId, tierId, body.containerId(), body.position());
        ContainerCatalog.ContainerInfo c = result.container();
        return new BlocksResponse.AssignResponse(result.tier().getId(), result.position(), "OCCUPIED",
                c.id(), c.containerNumber(), c.containerType());
    }

    @DeleteMapping("/blocks/{blockId}/bays/{bayId}/rows/{rowId}/tiers/{tierId}/container")
    @PreAuthorize("hasAnyRole('ADMIN','YARD_MANAGER')")
    public BlocksResponse.RemoveContainerResponse remove(@PathVariable UUID blockId, @PathVariable UUID bayId,
                                                         @PathVariable UUID rowId, @PathVariable UUID tierId) {
        YardAssignmentService.ReleaseResult result = assignment.release(blockId, bayId, rowId, tierId);
        return new BlocksResponse.RemoveContainerResponse(result.removedContainerId(), result.position(),
                result.removedAt(), null);
    }

    private long bayTotalTiers(UUID bayId) {
        long max = 0;
        for (com.smartportx.yard.domain.YardRow row : query.rowsByBay(bayId)) {
            max = Math.max(max, row.getTierCount());
        }
        return max;
    }

    private Double blockMaxWeight(UUID blockId) {
        return blockRepository.findById(blockId).map(YardBlock::getMaxStackWeight).orElse(null);
    }

    private int stackCount(YardQueryService.TierContext ctx) {
        return (int) ctx.row().getTierCount();
    }
}