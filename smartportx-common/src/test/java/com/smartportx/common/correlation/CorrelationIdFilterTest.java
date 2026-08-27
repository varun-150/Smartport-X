package com.smartportx.common.correlation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void cleanUp() {
        CorrelationContext.clear();
    }

    @Test
    void generatesCorrelationIdWhenHeaderAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        List<String> seen = new ArrayList<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.add(CorrelationContext.current().orElseThrow());
            }
        };
        filter.doFilter(request, response, chain);
        assertThat(seen).hasSize(1);
        UUID.fromString(seen.get(0)); // valid uuid
        assertThat(response.getHeader(CorrelationContext.HEADER)).isEqualTo(seen.get(0));
        // cleared after the chain
        assertThat(CorrelationContext.current()).isEmpty();
        assertThat(MDC.get(CorrelationContext.MDC_KEY)).isNull();
    }

    @Test
    void propagatesInboundHeaderAndEchoesIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");
        request.addHeader(CorrelationContext.HEADER, "trace-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        List<String> seenMdc = new ArrayList<>();
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenMdc.add(MDC.get(CorrelationContext.MDC_KEY));
            }
        });
        assertThat(seenMdc).containsExactly("trace-abc-123");
        assertThat(response.getHeader(CorrelationContext.HEADER)).isEqualTo("trace-abc-123");
    }

    @Test
    void responseStillGetsHeaderWhenChainFails() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            filter.doFilter(request, response, new MockFilterChain() {
                @Override
                public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                    throw new IllegalStateException("break");
                }
            });
        } catch (IllegalStateException expected) {
            // expected
        }
        assertThat(response.getHeader(CorrelationContext.HEADER)).isNotNull();
        assertThat(CorrelationContext.current()).isEmpty();
        assertThat(MDC.get(CorrelationContext.MDC_KEY)).isNull();
    }
}