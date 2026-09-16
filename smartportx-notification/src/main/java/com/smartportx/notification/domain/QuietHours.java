package com.smartportx.notification.domain;

/**
 * Quiet-window configuration. API-CONTRACTS &sect;9.2 preferences payload.
 */
public record QuietHours(
        boolean enabled,
        String start,
        String end,
        String timezone) {

    public static QuietHours defaults() {
        return new QuietHours(false, "22:00", "06:00", "UTC");
    }
}