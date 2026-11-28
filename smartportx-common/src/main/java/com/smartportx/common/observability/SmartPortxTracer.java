package com.smartportx.common.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

/**
 * Thin tracing helper (M2.5): starts spans named by the caller and correlates them
 * with the request {@code correlationId}. Wiring is Spring Boot's own auto-config for
 * actuator tracing (micrometer-tracing + OTLP exporter) — Boot supplies the
 * {@link Tracer} when tracing is enabled.
 */
public class SmartPortxTracer {

    private final Tracer tracer;

    public SmartPortxTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    public Span start(String spanName, String correlationId) {
        Span span = tracer.nextSpan().name(spanName);
        span.tag("correlationId", correlationId);
        span.start();
        return span;
    }
}