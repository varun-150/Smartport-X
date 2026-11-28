package com.smartportx.common.event;

import java.time.Instant;
import java.util.UUID;

import com.smartportx.common.correlation.CorrelationContext;

/**
 * SmartPortX domain event envelope (M2.4): Avro-free, JSON via Jackson.
 * {@code eventType} follows {@code {service}.{Type}} (e.g. {@code container.ContainerRegistered});
 * {@code eventId} is a fresh UUID per emission, {@code correlationId} carries the originating
 * request trace, {@code source} is the emitting service id.
 */
public record DomainEvent<T>(UUID eventId, String eventType, Instant occurredAt, String source,
                             String correlationId, T payload) {

    public static <T> DomainEvent<T> of(String eventType, String source, T payload) {
        return new DomainEvent<>(UUID.randomUUID(), eventType, Instant.now(), source,
                CorrelationContext.getOrCreate(), payload);
    }
}