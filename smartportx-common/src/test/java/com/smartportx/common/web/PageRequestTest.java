package com.smartportx.common.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageRequestTest {

    @Test
    void appliesDefaults() {
        PageRequest pr = PageRequest.from(-1, 0, null);
        assertThat(pr.page()).isZero();
        assertThat(pr.size()).isEqualTo(PageRequest.DEFAULT_SIZE);
        assertThat(pr.sort()).isEmpty();
    }

    @Test
    void capsSizeAtMax() {
        assertThat(PageRequest.from(0, 10000, null).size()).isEqualTo(PageRequest.MAX_SIZE);
    }

    @Test
    void parsesSortTokensAndDirections() {
        PageRequest pr = PageRequest.from(2, 25, "registeredAt,desc containerNumber,asc createdAt,asc");
        assertThat(pr.size()).isEqualTo(25);
        assertThat(pr.sort()).containsExactly(
                new PageRequest.SortOrder("registeredAt", false),
                new PageRequest.SortOrder("containerNumber", true),
                new PageRequest.SortOrder("createdAt", true));
    }

    @Test
    void toleratesBlankSort() {
        assertThat(PageRequest.from(0, 10, "   , , ").sort()).isEmpty();
    }

    @Test
    void pageResultComputesTotalPages() {
        PageResult<String> result = PageResult.of(List.of("a", "b"), 0, 20, 45);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.map(s -> s.toUpperCase()).content()).containsExactly("A", "B");
    }

    @Test
    void pageResultEdges() {
        assertThat(PageResult.of(List.of(), 0, 20, 0).totalPages()).isZero();
        assertThat(PageResult.of(List.of("a"), 0, 20, 1).totalPages()).isOne();
    }
}