package com.smartportx.common.web;

import java.util.ArrayList;
import java.util.List;

/**
 * Pagination + sorting request (M2.3). Bound from request params:
 * {@code page} (0-based, default 0), {@code size} (default 20, capped 100),
 * {@code sort} = comma/space separated {@code field[,asc|desc]} entries.
 */
public record PageRequest(int page, int size, List<SortOrder> sort) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageRequest {
        page = Math.max(0, page);
        size = size < 1 ? DEFAULT_SIZE : Math.min(MAX_SIZE, size);
        sort = sort == null ? List.of() : List.copyOf(sort);
    }

    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size, List.of());
    }

    /** @param sortCsv e.g. {@code "registeredAt,desc containerNumber,asc"} or {@code "registeredAt desc"}. */
    public static PageRequest from(int page, int size, String sortCsv) {
        List<SortOrder> orders = new ArrayList<>();
        if (sortCsv != null) {
            for (String token : sortCsv.trim().split("\\s+")) {
                if (token.isEmpty()) {
                    continue;
                }
                String[] parts = token.split(",");
                if (parts.length == 0 || parts[0].trim().isEmpty()) {
                    continue;
                }
                String field = parts[0].trim();
                boolean asc = parts.length < 2 || !"desc".equalsIgnoreCase(parts[1].trim());
                orders.add(new SortOrder(field, asc));
            }
        }
        return new PageRequest(page, size, orders);
    }

    /** Single sort order token. */
    public record SortOrder(String field, boolean asc) {
    }
}