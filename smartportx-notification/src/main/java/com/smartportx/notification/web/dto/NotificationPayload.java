package com.smartportx.notification.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartportx.notification.domain.NotificationCategory;
import com.smartportx.notification.domain.NotificationSeverity;
import com.smartportx.notification.service.NotificationPush;

import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket payload pushed on a topic. API-CONTRACTS &sect;9.1.
 */
public record NotificationPayload(
        String topic,
        String notificationId,
        NotificationCategory category,
        NotificationSeverity severity,
        String title,
        String message,
        Instant timestamp,
        String actionUrl,
        JsonNode data) {

    public static NotificationPayload from(NotificationPush push) {
        return new NotificationPayload(
                push.topic(),
                "ntf-" + UUID.randomUUID(),
                push.category(),
                push.severity(),
                push.title(),
                push.message(),
                Instant.now(),
                push.actionUrl(),
                push.data());
    }
}