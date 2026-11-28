package com.smartportx.common.web;

import java.util.List;
import java.util.function.Function;

/** Uniform page slice returned by SmartPortX services (M2.3). */
public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResult<T> of(List<T> content, int page, int size, long totalElements) {
        int effectiveSize = Math.max(1, size);
        int totalPages = (int) ((totalElements + effectiveSize - 1) / effectiveSize);
        return new PageResult<>(List.copyOf(content), page, effectiveSize, totalElements, totalPages);
    }

    /** Map content while preserving paging metadata. */
    public <R> PageResult<R> map(Function<T, R> mapper) {
        List<R> mapped = content.stream().map(mapper).toList();
        return new PageResult<>(mapped, page, size, totalElements, totalPages);
    }
}