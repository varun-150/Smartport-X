package com.smartportx.common.monitoring;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressResolverTest {

    private static final List<String> TRUSTED = List.of("172.16.0.0/12");

    @Test
    void trustsForwardedHeaderOnlyFromTrustedProxy() {
        assertThat(ClientAddressResolver.resolve("172.20.0.5", "203.0.113.9, 172.20.0.5", TRUSTED))
                .isEqualTo("203.0.113.9");
    }

    @Test
    void ignoresForwardedHeaderFromUntrustedPeer() {
        assertThat(ClientAddressResolver.resolve("8.8.8.8", "203.0.113.9", TRUSTED))
                .isEqualTo("8.8.8.8");
    }

    @Test
    void skipsSpoofedNonIpTokens() {
        assertThat(ClientAddressResolver.resolve("172.20.0.5", "not-an-ip, 203.0.113.9", TRUSTED))
                .isEqualTo("203.0.113.9");
    }

    @Test
    void fallsBackToPeerWhenHeaderMissingOrMalformed() {
        assertThat(ClientAddressResolver.resolve("172.20.0.5", null, TRUSTED)).isEqualTo("172.20.0.5");
        assertThat(ClientAddressResolver.resolve("172.20.0.5", "garbage", TRUSTED)).isEqualTo("172.20.0.5");
    }

    @Test
    void handlesMissingPeer() {
        assertThat(ClientAddressResolver.resolve(null, "203.0.113.9", TRUSTED)).isEqualTo("unknown");
    }
}
