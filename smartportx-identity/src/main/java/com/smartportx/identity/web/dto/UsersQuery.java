package com.smartportx.identity.web.dto;

import com.smartportx.identity.domain.RoleName;

public record UsersQuery(
        Integer limit,
        String cursor,
        RoleName role,
        Status status,
        String search,
        String sort) {

    public Integer effectiveLimit() {
        if (limit == null || limit < 1) {
            return 25;
        }
        return Math.min(limit, 100);
    }
}