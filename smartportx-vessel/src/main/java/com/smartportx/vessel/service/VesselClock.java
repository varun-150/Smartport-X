package com.smartportx.vessel.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class VesselClock {

    private final Clock clock;

    public VesselClock() {
        this(Clock.systemUTC());
    }

    public VesselClock(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }
}