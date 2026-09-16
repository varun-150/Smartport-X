package com.smartportx.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartportx.notification.domain.NotificationCategory;
import com.smartportx.notification.domain.NotificationHistory;
import com.smartportx.notification.domain.NotificationSeverity;
import com.smartportx.notification.repository.NotificationHistoryRepository;
import org.springframework.stereotype.Service;

/**
 * Persists delivery records (DATA-ARCHITECTURE &sect;11.2). Broadcast records carry a
 * {@code null} user id and WEBSOCKET channel because they are fan-out events, not
 * addressed to a single recipient.
 */
@Service
public class NotificationHistoryService {

    private final NotificationHistoryRepository repository;

    public NotificationHistoryService(NotificationHistoryRepository repository) {
        this.repository = repository;
    }

    public NotificationHistory record(NotificationCategory category, NotificationSeverity severity,
                                      String title, String message, String actionUrl, JsonNode data) {
        return repository.save(NotificationHistory.broadcast(category, severity, title, message, actionUrl, data));
    }
}