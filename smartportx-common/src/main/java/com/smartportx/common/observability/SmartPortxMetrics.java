package com.smartportx.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;

/**
 * SmartPortX default metrics (M2.5): HTTP request count by status class and
 * domain-event publish/consume count by event type. Present in every service that
 * carries a {@link MeterRegistry} (actuator on the classpath); falls back to an
 * in-memory {@link SimpleMeterRegistry} when no backend registry is configured yet.
 */
public class SmartPortxMetrics {

    public static final String HTTP_REQUESTS = "smartportx.http.requests";
    public static final String EVENTS_PUBLISHED = "smartportx.events.published";
    public static final String EVENTS_CONSUMED = "smartportx.events.consumed";

    private final MeterRegistry registry;

    public SmartPortxMetrics(ObjectProvider<MeterRegistry> provider) {
        MeterRegistry resolved = provider.getIfAvailable();
        this.registry = resolved != null ? resolved : new SimpleMeterRegistry();
    }

    public void recordHttp(int status) {
        Counter.builder(HTTP_REQUESTS)
                .tags(Tags.of("status_class", Integer.toString(status / 100)))
                .register(registry)
                .increment();
    }

    public void eventPublished(String eventType) {
        Counter.builder(EVENTS_PUBLISHED)
                .tags(Tags.of("event_type", eventType))
                .register(registry)
                .increment();
    }

    public void eventConsumed(String eventType) {
        Counter.builder(EVENTS_CONSUMED)
                .tags(Tags.of("event_type", eventType))
                .register(registry)
                .increment();
    }
}