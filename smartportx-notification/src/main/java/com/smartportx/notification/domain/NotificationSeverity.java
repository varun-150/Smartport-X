package com.smartportx.notification.domain;

/**
 * Severity levels for notifications. API-CONTRACTS &sect;9.1 payload.
 */
public enum NotificationSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL;

    public static NotificationSeverity from(String value) {
        if ("CRITICAL".equalsIgnoreCase(value)) {
            return CRITICAL;
        }
        if ("HIGH".equalsIgnoreCase(value) || "WARNING".equalsIgnoreCase(value)) {
            return WARNING;
        }
        if ("OUT_OF_SERVICE".equalsIgnoreCase(value)
                || "HELD".equalsIgnoreCase(value)
                || "UNDER_REVIEW".equalsIgnoreCase(value)
                || "EXAMINATION".equalsIgnoreCase(value)) {
            return WARNING;
        }
        if ("ERROR".equalsIgnoreCase(value)) {
            return ERROR;
        }
        return INFO;
    }

    public static NotificationSeverity defaultFrom(String value) {
        if ("OUT_OF_SERVICE".equalsIgnoreCase(value)
                || "HELD".equalsIgnoreCase(value)
                || "UNDER_REVIEW".equalsIgnoreCase(value)
                || "EXAMINATION".equalsIgnoreCase(value)) {
            return WARNING;
        }
        return INFO;
    }
}