package com.smartportx.common.monitoring;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringNetworkAccessTest {

    private static final List<String> NETWORKS =
            List.of("127.0.0.1/32", "::1/128", "172.16.0.0/12");

    @Test
    void allowsLoopbackAndDockerBridge() {
        assertThat(MonitoringNetworkAccess.isAllowed("127.0.0.1", NETWORKS)).isTrue();
        assertThat(MonitoringNetworkAccess.isAllowed("0:0:0:0:0:0:0:1", NETWORKS)).isTrue();
        assertThat(MonitoringNetworkAccess.isAllowed("172.20.5.7", NETWORKS)).isTrue();
    }

    @Test
    void allowsIpv4MappedLoopback() {
        assertThat(MonitoringNetworkAccess.isAllowed("::ffff:127.0.0.1", NETWORKS)).isTrue();
    }

    @Test
    void rejectsPublicAddresses() {
        assertThat(MonitoringNetworkAccess.isAllowed("8.8.8.8", NETWORKS)).isFalse();
        assertThat(MonitoringNetworkAccess.isAllowed("192.168.1.10", NETWORKS)).isFalse();
    }

    @Test
    void rejectsBlankAndMalformedInput() {
        assertThat(MonitoringNetworkAccess.isAllowed(null, NETWORKS)).isFalse();
        assertThat(MonitoringNetworkAccess.isAllowed("", NETWORKS)).isFalse();
        assertThat(MonitoringNetworkAccess.isAllowed("not-an-ip", NETWORKS)).isFalse();
        assertThat(MonitoringNetworkAccess.isAllowed("127.0.0.1", List.of())).isFalse();
    }

    @Test
    void supportsExactAddresses() {
        assertThat(MonitoringNetworkAccess.isAllowed("10.1.2.3", List.of("10.1.2.3"))).isTrue();
        assertThat(MonitoringNetworkAccess.isAllowed("10.1.2.4", List.of("10.1.2.3"))).isFalse();
    }
}
