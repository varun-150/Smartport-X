package com.smartportx.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartportx.notification.domain.NotificationCategory;
import com.smartportx.notification.domain.NotificationSeverity;
import com.smartportx.notification.web.dto.NotificationPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Fan-out point for STOMP notifications. Pushes a {@link NotificationPayload} to the
 * resolved topic and records it in history for auditability.
 */
@Service
public class NotificationBroadcaster {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationBroadcaster.class);

    public static final String TOPIC_ALERTS = "/topic/alerts";

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationHistoryService historyService;

    public NotificationBroadcaster(SimpMessagingTemplate messagingTemplate,
                                   NotificationHistoryService historyService) {
        this.messagingTemplate = messagingTemplate;
        this.historyService = historyService;
    }

    public void broadcast(NotificationPush push) {
        NotificationPayload payload = NotificationPayload.from(push);
        messagingTemplate.convertAndSend(push.topic(), payload);
        historyService.record(push.category(), push.severity(), push.title(), push.message(),
                push.actionUrl(), push.data());
        LOG.debug("broadcast {} to {}", push.category(), push.topic());
    }

    public void broadcastAlert(NotificationSeverity severity, String title, String message,
                               String actionUrl, JsonNode data) {
        NotificationPush push = new NotificationPush(TOPIC_ALERTS, NotificationCategory.ALERT, severity,
                title, message, actionUrl, data);
        NotificationPayload payload = NotificationPayload.from(push);
        messagingTemplate.convertAndSend(TOPIC_ALERTS, payload);
        messagingTemplate.convertAndSend(TOPIC_ALERTS + "/" + severity.name().toLowerCase(), payload);
        historyService.record(NotificationCategory.ALERT, severity, title, message, actionUrl, data);
        LOG.debug("broadcast alert [{}] {}", severity, title);
    }
}