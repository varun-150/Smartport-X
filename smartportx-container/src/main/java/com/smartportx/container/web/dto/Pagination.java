package com.smartportx.container.web.dto;

public record Pagination(String nextCursor, boolean hasMore, long totalCount, int limit) {
}