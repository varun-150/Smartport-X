package com.smartportx.integration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Ingested EDIFACT message and its parse/mapping lifecycle. DATA-ARCHITECTURE &sect;10.1.
 * The (sender, receiver, interchange_ref, message_ref) tuple is unique (M8.5 idempotency).
 */
@Entity
@Table(name = "edifact_messages", schema = "smartportx_integration")
public class EdifactMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_type", nullable = false, length = 10)
    private String messageType;

    @Column(name = "raw_message", nullable = false)
    private String rawMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_data", columnDefinition = "jsonb")
    private Map<String, Object> parsedData;

    @Column(name = "sender", length = 50)
    private String sender;

    @Column(name = "receiver", length = 50)
    private String receiver;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction = "INBOUND";

    @Column(name = "interchange_ref", length = 50)
    private String interchangeRef;

    @Column(name = "message_ref", length = 50)
    private String messageRef;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_errors", columnDefinition = "jsonb")
    private Map<String, Object> validationErrors;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EdifactStatus status = EdifactStatus.RECEIVED;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        receivedAt = now;
        createdAt = now;
        updatedAt = now;
        if (direction == null || direction.isBlank()) {
            direction = "INBOUND";
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public void setRawMessage(String rawMessage) {
        this.rawMessage = rawMessage;
    }

    public Map<String, Object> getParsedData() {
        return parsedData;
    }

    public void setParsedData(Map<String, Object> parsedData) {
        this.parsedData = parsedData;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getInterchangeRef() {
        return interchangeRef;
    }

    public void setInterchangeRef(String interchangeRef) {
        this.interchangeRef = interchangeRef;
    }

    public String getMessageRef() {
        return messageRef;
    }

    public void setMessageRef(String messageRef) {
        this.messageRef = messageRef;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(UUID correlationId) {
        this.correlationId = correlationId;
    }

    public Map<String, Object> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(Map<String, Object> validationErrors) {
        this.validationErrors = validationErrors;
    }

    public EdifactStatus getStatus() {
        return status;
    }

    public void setStatus(EdifactStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
