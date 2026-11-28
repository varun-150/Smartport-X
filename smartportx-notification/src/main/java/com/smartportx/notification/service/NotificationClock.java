package com.smartportx.notification.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Single time authority for notification rules (alert deduplication windows).
 * Tests replace this bean with a controlled mock.
 */
@Service
public class NotificationClock {

    private final Clock clock;

    public NotificationClock() {
        this(Clock.systemUTC());
    }

    public NotificationClock(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }
}