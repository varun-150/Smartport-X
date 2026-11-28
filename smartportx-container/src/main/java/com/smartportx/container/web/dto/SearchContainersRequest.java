package com.smartportx.container.web.dto;

import com.smartportx.container.domain.ContainerStatus;
import com.smartportx.container.domain.ContainerType;

import java.time.Instant;
import java.util.List;

public record SearchContainersRequest(
        String containerNumber,
        List<ContainerStatus> status,
        List<ContainerType> type,
        String owner,
        LocationFilter location,
        WeightRange weight,
        Boolean dangerousGoods,
        String customsStatus,
        String sort,
        Integer limit) {

    public Integer effectiveLimit() {
        if (limit == null || limit < 1) {
            return 25;
        }
        return Math.min(limit, 100);
    }

    public record LocationFilter(String block, Instant fromDate, Instant toDate) {
    }

    public record WeightRange(Double min, Double max) {
    }
}