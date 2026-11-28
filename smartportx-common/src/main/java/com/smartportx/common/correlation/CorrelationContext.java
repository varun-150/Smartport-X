package com.smartportx.common.correlation;

import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;

/**
 * Request correlation id: held in an {@link InheritableThreadLocal} and mirrored into
 * SLF4J MDC (key {@value #MDC_KEY}) so every log line in the request carries it.
 */
public final class CorrelationContext {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final ThreadLocal<String> CURRENT = new InheritableThreadLocal<>();

    private CorrelationContext() {
    }

    static void set(String id) {
        CURRENT.set(id);
        MDC.put(MDC_KEY, id);
    }

    /** The current correlation id assigned by {@link CorrelationIdFilter}, if any. */
    public static Optional<String> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** Current id, else a freshly generated UUID (for event emission outside a request). */
    public static String getOrCreate() {
        String current = CURRENT.get();
        if (current == null) {
            current = uuid();
            set(current);
        }
        return current;
    }

    static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static void clear() {
        CURRENT.remove();
        MDC.remove(MDC_KEY);
    }
}