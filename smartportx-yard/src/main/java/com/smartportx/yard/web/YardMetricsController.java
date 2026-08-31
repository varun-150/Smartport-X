package com.smartportx.yard.web;

import com.smartportx.yard.service.YardMetricsService;
import com.smartportx.yard.web.dto.MetricsResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/yard/metrics")
public class YardMetricsController {

    private final YardMetricsService service;

    public YardMetricsController(YardMetricsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public MetricsResponse metrics(@RequestParam(defaultValue = "24h") String period) {
        return MetricsResponse.from(period, service.metrics(period));
    }

    @GetMapping("/blocks")
    @PreAuthorize("isAuthenticated()")
    public MetricsResponse.BlockMetricsList blockMetrics() {
        List<MetricsResponse.BlockMetricsList.BlockMetricsData> data = new ArrayList<>();
        for (YardMetricsService.BlockMetrics bm : service.blockMetrics()) {
            data.add(new MetricsResponse.BlockMetricsList.BlockMetricsData(bm.blockId(), bm.blockCode(),
                    bm.blockType(), bm.totalSlots(), bm.occupiedSlots(), bm.utilizationRate(),
                    bm.averageDwellDays(), bm.reshufflingRate(), bm.dangerousGoodsSlots(),
                    bm.refrigeratedSlots(), bm.emptySlots()));
        }
        return new MetricsResponse.BlockMetricsList(data);
    }
}