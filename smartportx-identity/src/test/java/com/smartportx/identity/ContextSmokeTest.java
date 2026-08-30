package com.smartportx.identity;

import com.smartportx.common.correlation.CorrelationIdFilter;
import com.smartportx.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 verification preserved through M3: proves the smartportx-common auto-configuration
 * still activates inside the identity context (correlation filter, RFC 9457 handler,
 * shared metrics/health) once JPA, Flyway and security are wired in.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "eureka.client.enabled=false"
})
@ActiveProfiles("test")
class ContextSmokeTest {

    @Autowired
    ApplicationContext context;

    @Test
    void commonStarterWiresIntoIdentityContext() {
        assertThat(context.getBean(GlobalExceptionHandler.class)).isNotNull();
        assertThat(context.getBean(CorrelationIdFilter.class)).isNotNull();
        assertThat(context.getBean("smartPortxMetrics")).isNotNull();
        assertThat(context.getBean("smartPortxHealthIndicator")).isNotNull();
    }
}