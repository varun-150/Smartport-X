package com.smartportx.notification.domain;

/**
 * Delivery channel of a notification history record. DATA-ARCHITECTURE &sect;11.2.
 */
public enum Channel {
    EMAIL,
    WEBSOCKET,
    BOTH
}