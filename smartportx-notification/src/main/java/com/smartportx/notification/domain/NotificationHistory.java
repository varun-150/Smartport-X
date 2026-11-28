package com.smartportx.notification.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent record of a delivered notification. DATA-ARCHITECTURE &sect;11.2 extended
 * with the severity / action URL / data snapshot superset fields.
 */
@Entity
@Table(name = "notification_history", schema = "smartportx_notification")
public class NotificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private NotificationSeverity severity = NotificationSeverity.INFO;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeliveryStatus status = DeliveryStatus.SENT;

    @Column(name = "action_url", length = 500)
    private String actionUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", columnDefinition = "jsonb")
    private JsonNode data;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;

    public NotificationHistory() {
    }

    private NotificationHistory(UUID userId, NotificationCategory category, NotificationSeverity severity,
                                String title, String message, Channel channel, String actionUrl,
                                JsonNode data, Instant sentAt) {
        this.userId = userId;
        this.category = category;
        this.severity = severity;
        this.title = title;
        this.message = message;
        this.channel = channel;
        this.actionUrl = actionUrl;
        this.data = data;
        this.sentAt = sentAt;
    }

    public static NotificationHistory broadcast(NotificationCategory category, NotificationSeverity severity,
                                                String title, String message, String actionUrl, JsonNode data) {
        return new NotificationHistory(null, category, severity, title, message, Channel.WEBSOCKET, actionUrl, data,
                Instant.now());
    }

    public void markRead() {
        this.readAt = Instant.now();
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public NotificationCategory category() {
        return category;
    }

    public NotificationSeverity severity() {
        return severity;
    }

    public String title() {
        return title;
    }

    public String message() {
        return message;
    }

    public Channel channel() {
        return channel;
    }

    public DeliveryStatus status() {
        return status;
    }

    public String actionUrl() {
        return actionUrl;
    }

    public JsonNode data() {
        return data;
    }

    public Instant sentAt() {
        return sentAt;
    }

    public enum DeliveryStatus {
        SENT,
        DELIVERED,
        FAILED
    }
}