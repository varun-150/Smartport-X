package com.smartportx.container.web.dto;

import com.smartportx.container.domain.ContainerStatus;
import com.smartportx.container.domain.ContainerType;

import java.time.Instant;

public record ContainersQuery(
        Integer limit,
        String cursor,
        String sort,
        ContainerStatus status,
        ContainerType type,
        String owner,
        Boolean hasDangerousGoods,
        String location,
        Instant createdAfter,
        Instant createdBefore) {

    public Integer effectiveLimit() {
        if (limit == null || limit < 1) {
            return 25;
        }
        return Math.min(limit, 100);
    }
}