package com.smartportx.identity;

import com.smartportx.common.correlation.CorrelationIdFilter;
import com.smartportx.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 verification: proves the smartportx-common auto-configuration activates inside a
 * servlet service (identity) — correlation filter injectable, RFC 9457 handler registered,
 * shared metrics/health present. Prevents "dependency present but inert" drift.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "eureka.client.enabled=false",
        "spring.main.allow-bean-definition-overriding=true"
})
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