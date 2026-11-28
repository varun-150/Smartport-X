package com.smartportx.common.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.core.env.Environment;

/**
 * Small additive health indicator (M2.5): reports the service name/version so the
 * dashboard and load-balancer see a per-service identity on {@code /actuator/health}.
 * Never fails the aggregate; real dependency probes are per-service indicators.
 */
public class SmartPortxHealthIndicator implements HealthIndicator {

    private final String serviceName;
    private final String version;

    public SmartPortxHealthIndicator(Environment environment) {
        this.serviceName = environment.getProperty("spring.application.name", "smartportx");
        this.version = environment.getProperty("smartportx.version", environment.getProperty("java.version", "unknown"));
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetail("service", serviceName)
                .withDetail("version", version)
                .build();
    }
}