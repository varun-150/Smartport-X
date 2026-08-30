package com.smartportx.container.web.dto;

public record EventsQuery(
        Integer limit,
        String cursor,
        String eventType) {

    public Integer effectiveLimit() {
        if (limit == null || limit < 1) {
            return 50;
        }
        return Math.min(limit, 200);
    }
}