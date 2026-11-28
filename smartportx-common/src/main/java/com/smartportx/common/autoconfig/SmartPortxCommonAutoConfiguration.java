package com.smartportx.common.autoconfig;

import com.smartportx.common.correlation.CorrelationIdFilter;
import com.smartportx.common.error.GlobalExceptionHandler;
import com.smartportx.common.observability.SmartPortxHealthIndicator;
import com.smartportx.common.observability.SmartPortxMetrics;
import com.smartportx.common.observability.SmartPortxTracer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * SmartPortX common auto-configuration (M2.6): module-dependency starter.
 * <ul>
 *   <li>{@link CorrelationIdFilter} + {@link GlobalExceptionHandler} — servlet (MVC) services only
 *       (reactive gateway is intentionally excluded; it owns its own WebFlux error handling).</li>
 *   <li>{@link SmartPortxMetrics}, {@link SmartPortxHealthIndicator}, {@link SmartPortxTracer} —
 *       activated when actuator / micrometer tracing is present.</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SmartPortxCommonAutoConfiguration {

    @Bean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    public SmartPortxMetrics smartPortxMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        return new SmartPortxMetrics(registryProvider);
    }

    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    public SmartPortxHealthIndicator smartPortxHealthIndicator(Environment environment) {
        return new SmartPortxHealthIndicator(environment);
    }

    @Bean
    @ConditionalOnBean(Tracer.class)
    public SmartPortxTracer smartPortxTracer(Tracer tracer) {
        return new SmartPortxTracer(tracer);
    }
}