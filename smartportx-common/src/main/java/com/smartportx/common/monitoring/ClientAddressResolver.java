package com.smartportx.common.monitoring;

import java.util.List;

/**
 * Resolves the effective client address for audit logging and per-IP rate limiting.
 *
 * <p>{@code X-Forwarded-For} is a client-controlled header. It is consulted ONLY when the
 * immediate TCP peer is an explicitly trusted proxy (for example the Docker bridge where
 * Spring Cloud Gateway runs). Otherwise the socket peer address is used, so an external
 * caller cannot spoof the header to evade login rate limits or poison audit records.</p>
 */
public final class ClientAddressResolver {

    private ClientAddressResolver() {
    }

    public static String resolve(String remoteAddress, String forwardedFor, List<String> trustedProxyNetworks) {
        String peer = clean(remoteAddress);
        if (peer == null || !MonitoringNetworkAccess.isAllowed(peer, trustedProxyNetworks)) {
            return peer == null ? "unknown" : peer;
        }
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return peer;
        }
        // Left-most valid IP wins; skip injected non-IP tokens rather than trusting them.
        for (String token : forwardedFor.split(",")) {
            String candidate = clean(token);
            if (MonitoringNetworkAccess.isValidIpAddress(candidate)) {
                return candidate;
            }
        }
        return peer;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
