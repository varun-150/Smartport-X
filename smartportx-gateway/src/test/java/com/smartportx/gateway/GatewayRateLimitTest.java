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
 * Rate-limit boundary: exhaustion returns 429 problem+json with a {@code Retry-After}
 * header, keyed per route + user. Uses a tiny capacity so the bucket drains within the
 * test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(classes = GatewayTestSecurityConfig.class)
class GatewayRateLimitTest {

    @LocalServerPort
    private int port;

    private static StubUpstreamServer stub;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("GATEWAY_URI_SMARTPORTX_CONTAINER", () -> "http://127.0.0.1:" + stub.port());
        registry.add("smartportx.gateway.rate-limit.capacity", () -> 1);
        registry.add("smartportx.gateway.rate-limit.refill-per-minute", () -> 0);
    }

    @BeforeAll
    static void startStub() throws IOException {
        stub = StubUpstreamServer.start();
    }

    @AfterAll
    static void stopStub() {
        if (stub != null) {
            stub.close();
        }
    }

    @Test
    void rateLimitExhaustionYields429ProblemJsonWithRetryAfter() {
        WebTestClient client = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + port).build();

        client.get().uri("/api/v1/containers/CTN-777001")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + GatewayTestTokens.admin())
                .exchange()
                .expectStatus().isOk();

        client.get().uri("/api/v1/containers/CTN-777002")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + GatewayTestTokens.admin())
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().exists("Retry-After")
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:smartportx:problems:rate_limited")
                .jsonPath("$.code").isEqualTo("rate_limited")
                .jsonPath("$.status").isEqualTo(429);
    }
}