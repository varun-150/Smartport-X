package com.smartportx.common;

/**
 * Marker entry point for the shared library module.
 *
 * <p>Concrete shared API (RFC 9457 {@code ProblemDetail} helpers, correlation-id filter, domain event
 * envelope, observability auto-configuration) is implemented in Phase 4 M2
 * ({@code .smartport/PHASE-4-IMPLEMENTATION-PLAN.md}, increments M2.1-M2.6).</p>
 */
public final class SmartPortxCommon {

    private SmartPortxCommon() {
        // utility holder - not instantiable
    }
}