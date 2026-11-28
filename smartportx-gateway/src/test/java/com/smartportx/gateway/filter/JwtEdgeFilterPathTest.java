package com.smartportx.gateway.filter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtEdgeFilterPathTest {

    @Test
    void allowsPrimaryWebSocketEndpointWithoutConfusingPrefixNeighbors() {
        assertThat(JwtEdgeFilter.isWebSocketPath("/ws")).isTrue();
        assertThat(JwtEdgeFilter.isWebSocketPath("/ws/")).isTrue();
        assertThat(JwtEdgeFilter.isWebSocketPath("/ws/notifications")).isTrue();
        assertThat(JwtEdgeFilter.isWebSocketPath("/ws/notifications/info")).isTrue();
        assertThat(JwtEdgeFilter.isWebSocketPath("/wsfoo")).isFalse();
    }
}
