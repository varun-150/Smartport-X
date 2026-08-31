package com.smartportx.yard.web.dto;

public record PaginationResponse(String nextCursor, boolean hasMore, long totalCount, int limit) {
}