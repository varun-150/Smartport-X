package com.smartportx.container.web.dto;

import com.smartportx.container.domain.MovementType;

import java.time.Instant;

public record MovementsQuery(
        Integer limit,
        String cursor,
        Instant from,
        Instant to,
        MovementType type) {

    public Integer effectiveLimit() {
        if (limit == null || limit < 1) {
            return 50;
        }
        return Math.min(limit, 200);
    }
}