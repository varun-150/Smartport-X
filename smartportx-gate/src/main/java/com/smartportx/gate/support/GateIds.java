package com.smartportx.gate.support;

import com.smartportx.common.error.ApiException;

import java.util.UUID;

/**
 * Gate API ids are prefixed on the wire ({@code apt-<uuid>}, {@code txn-<uuid>},
 * {@code quota-<uuid>}); callers may also pass the bare UUID. This codec normalises
 * both forms to a UUID and renders the prefixed form for responses. See
 * PHASE-4-DECISIONS D-446 (API-CONTRACTS &sect;6 uses prefixed ids on responses yet
 * plain UUIDs for some path parameters).
 */
public final class GateIds {

    public static final String APPOINTMENT_PREFIX = "apt";
    public static final String TRANSACTION_PREFIX = "txn";
    public static final String QUOTA_PREFIX = "quota";

    private GateIds() {
    }

    public static UUID toUuid(String prefixedOrBare, String expectedPrefix) {
        if (prefixedOrBare == null || prefixedOrBare.isBlank()) {
            throw new ApiException(400, "malformed_request", "Identifier is required");
        }
        String value = prefixedOrBare;
        if (expectedPrefix != null
                && prefixedOrBare.regionMatches(true, 0, expectedPrefix + "-", 0, expectedPrefix.length() + 1)) {
            value = prefixedOrBare.substring(expectedPrefix.length() + 1);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(400, "malformed_request",
                    "Identifier must start with '" + expectedPrefix + "-' or be a plain UUID");
        }
    }

    public static String appointment(UUID id) {
        return prefix(APPOINTMENT_PREFIX, id);
    }

    public static String transaction(UUID id) {
        return prefix(TRANSACTION_PREFIX, id);
    }

    public static String quota(UUID id) {
        return prefix(QUOTA_PREFIX, id);
    }

    private static String prefix(String prefix, UUID id) {
        return prefix + "-" + id;
    }
}