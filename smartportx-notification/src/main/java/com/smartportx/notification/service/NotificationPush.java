package com.smartportx.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartportx.notification.domain.NotificationCategory;
import com.smartportx.notification.domain.NotificationSeverity;

/**
 * Immutable outbound notification resolved from a domain event by the event router.
 */
public record NotificationPush(
        String topic,
        NotificationCategory category,
        NotificationSeverity severity,
        String title,
        String message,
        String actionUrl,
        JsonNode data) {
}