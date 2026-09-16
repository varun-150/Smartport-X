package com.smartportx.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;

/**
 * Resilience boundaries for M10.6: upstream timeout surfaces as 504 problem+json, and a
 * dead downstream triggers the per-route CircuitBreaker fallback producing 503
 * problem+json.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(classes = GatewayTestSecurityConfig.class)
class GatewayResilienceTest {

    @LocalServerPort
    private int port;

    private static StubUpstreamServer slowStub;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gateway.server.webflux.httpclient.response-timeout", () -> "500ms");
        registry.add("spring.cloud.gateway.server.webflux.httpclient.connect-timeout", () -> 500);
        registry.add("GATEWAY_URI_SMARTPORTX_CONTAINER", () -> "http://127.0.0.1:" + slowStub.port());
        registry.add("GATEWAY_URI_SMARTPORTX_VESSEL", () -> "http://127.0.0.1:1");
    }

    @BeforeAll
    static void startStub() throws IOException {
        slowStub = StubUpstreamServer.start();
    }

    @AfterAll
    static void stopStub() {
        if (slowStub != null) {
            slowStub.close();
        }
    }

    @Test
    void upstreamTimeoutYields504ProblemJson() {
        WebTestClient client = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + port).build();

        client.get().uri("/api/v1/containers/slow")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + GatewayTestTokens.admin())
                .exchange()
                .expectStatus().isEqualTo(504)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:smartportx:problems:gateway_timeout")
                .jsonPath("$.code").isEqualTo("gateway_timeout")
                .jsonPath("$.status").isEqualTo(504);
    }

    @Test
    void deadDownstreamTripsCircuitBreakerFallbackYielding503ProblemJson() {
        WebTestClient client = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + port).build();

        client.get().uri("/api/v1/vessels/VS-1001")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + GatewayTestTokens.admin())
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:smartportx:problems:service_unavailable")
                .jsonPath("$.code").isEqualTo("service_unavailable")
                .jsonPath("$.status").isEqualTo(503);
    }
}