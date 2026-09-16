package com.smartportx.integration.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Config-driven simulated telemetry generator (FR-011, D-007). When
 * {@code smartportx.integration.iot.enabled=true} it emits synthetic container-device
 * readings on a timer through {@link IotIngestService}, exercising the real ingestion,
 * threshold and alert pipeline. Disabled by default; a live device feed replaces it later.
 */
@Service
@ConditionalOnProperty(name = "smartportx.integration.iot.enabled", havingValue = "true")
public class IotSimulationService {

    private static final Logger log = LoggerFactory.getLogger(IotSimulationService.class);
    private static final List<String> SENSORS = List.of("TEMPERATURE", "GPS", "SHOCK", "BATTERY");

    private final IotIngestService ingest;
    private final List<String> containers;
    private final long intervalMs;

    public IotSimulationService(IotIngestService ingest,
                                @Value("${smartportx.integration.iot.simulated-containers:MSKU1234567,MSKU7654321,CMAU4455667}") List<String> containers,
                                @Value("${smartportx.integration.iot.interval-ms:15000}") long intervalMs) {
        this.ingest = ingest;
        this.containers = containers;
        this.intervalMs = intervalMs;
    }

    @Scheduled(fixedDelayString = "${smartportx.integration.iot.interval-ms:15000}", initialDelayString = "5000")
    public void simulateTick() {
        if (containers.isEmpty()) {
            return;
        }
        String container = containers.get(ThreadLocalRandom.current().nextInt(containers.size()));
        for (String sensor : SENSORS) {
            String value = sample(sensor);
            ingest.ingest(new IotIngestService.ReadingInput(
                    container, sensor, value, Instant.now(),
                    round(37.7749 + ThreadLocalRandom.current().nextDouble(-0.05, 0.05), 5),
                    round(-122.4194 + ThreadLocalRandom.current().nextDouble(-0.05, 0.05), 5),
                    ThreadLocalRandom.current().nextInt(10, 101),
                    ThreadLocalRandom.current().nextInt(0, 101)));
        }
        log.debug("IoT simulation tick for {} (interval={}ms)", container, intervalMs);
    }

    private String sample(String sensor) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        return switch (sensor) {
            case "TEMPERATURE" -> String.valueOf(round(22.0 + rnd.nextDouble(-8, 24), 1));
            case "SHOCK" -> String.valueOf(round(2.0 + rnd.nextDouble(0, 18), 1));
            case "BATTERY" -> String.valueOf(rnd.nextInt(5, 101));
            default -> String.format("%.5f,%.5f",
                    round(37.7 + rnd.nextDouble(-0.1, 0.1), 5),
                    round(-122.4 + rnd.nextDouble(-0.1, 0.1), 5));
        };
    }

    private double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
