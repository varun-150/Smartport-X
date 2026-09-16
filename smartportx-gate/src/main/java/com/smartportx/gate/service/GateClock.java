package com.smartportx.gate.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Single time authority for gate business rules (check-in window, book-ahead window,
 * confirmation/EIR numbering). Tests replace this bean with a controlled mock.
 */
@Service
public class GateClock {

    private final Clock clock;

    public GateClock() {
        this(Clock.systemUTC());
    }

    public GateClock(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate todayUtc() {
        return clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
    }
}