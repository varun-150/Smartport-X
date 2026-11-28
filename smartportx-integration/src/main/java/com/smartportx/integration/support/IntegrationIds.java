package com.smartportx.integration.support;

import com.smartportx.common.error.ApiException;

import java.util.UUID;

/**
 * Integration API ids. Message/reading ids are UUIDs on the wire; callers may also pass
 * the bare UUID. Kept as a thin codec for symmetry with the other services and to keep
 * id validation in one place.
 */
public final class IntegrationIds {

    public static final String MESSAGE_PREFIX = "edifact";

    private IntegrationIds() {
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

    public static String message(UUID id) {
        return MESSAGE_PREFIX + "-" + id;
    }
}
