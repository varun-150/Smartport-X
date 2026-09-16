package com.smartportx.vessel.support;

import com.smartportx.common.error.ApiException;

import java.util.UUID;

/**
 * Vessel API ids are prefixed on the wire ({@code vsl-<uuid>}, {@code sch-<uuid>},
 * {@code dp-<uuid>}); callers may also pass the bare UUID. This codec normalises
 * both forms to a UUID and renders the prefixed form for responses. See
 * PHASE-4-DECISIONS D-446 (pattern) / D-447 (vessel prefixes).
 */
public final class VesselIds {

    public static final String VESSEL_PREFIX = "vsl";
    public static final String SCHEDULE_PREFIX = "sch";
    public static final String PLAN_PREFIX = "dp";

    private VesselIds() {
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

    public static String vessel(UUID id) {
        return prefix(VESSEL_PREFIX, id);
    }

    public static String schedule(UUID id) {
        return prefix(SCHEDULE_PREFIX, id);
    }

    public static String plan(UUID id) {
        return prefix(PLAN_PREFIX, id);
    }

    private static String prefix(String prefix, UUID id) {
        return prefix + "-" + id;
    }
}