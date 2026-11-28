package com.smartportx.identity.web.dto;

import java.util.List;

public record UsersPage(
        Pagination pagination,
        List<UserResponse> data) {

    public record Pagination(String nextCursor, boolean hasMore, long totalCount, int limit) {
    }
}