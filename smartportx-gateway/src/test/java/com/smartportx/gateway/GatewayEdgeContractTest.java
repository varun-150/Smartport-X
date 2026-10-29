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
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Edge contract tests for the gateway against a JDK {@link StubUpstreamServer}: public
 * route bypass, RS256 JWT validation, role enforcement, trusted-header injection,
 * spoofed-header stripping, correlation propagation and 401/403 problem+json bodies.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(classes = GatewayTestSecurityConfig.class)
class GatewayEdgeContractTest {

    @LocalServerPort
    private int port;

    private static StubUpstreamServer stub;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        String upstream = "http://127.0.0.1:" + stub.port();
        registry.add("GATEWAY_URI_SMARTPORTX_CONTAINER", () -> upstream);
        registry.add("GATEWAY_URI_SMARTPORTX_IDENTITY", () -> upstream);
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

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    @Test
    void publicLoginRouteBypassesJwtFilterAndReachesUpstream() {
        client().get().uri("/api/v1/auth/login")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.path").isEqualTo("/api/v1/auth/login");
        assertThat(stub.lastAuthorization()).isNull();
    }

    @Test
    void authenticatedRequestProxiesToContainerWithTrustedHeaders() {
        tokenRequest(GatewayTestTokens.admin(), "/api/v1/containers/CTN-000123")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/containers/CTN-000123")
                .jsonPath("$.xUserId").isEqualTo(GatewayTestTokens.FIXED_SUBJECT)
                .jsonPath("$.xUserRole").isEqualTo("ADMIN")
                .jsonPath("$.authorizationPresent").isEqualTo("true");
        assertThat(stub.lastAuthorization()).startsWith("Bearer ");
    }

    @Test
    void spoofedClientHeadersAreStrippedAndReplacedWithTrustedValues() {
        client().get().uri("/api/v1/containers/CTN-000124")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + GatewayTestTokens.admin())
                .header("X-User-Id", "spoofed-user")
                .header("X-User-Role", "READONLY")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.xUserId").isEqualTo(GatewayTestTokens.FIXED_SUBJECT)
                .jsonPath("$.xUserRole").isEqualTo("ADMIN");
    }

    @Test
    void roleNotPermittedOnRouteYields403ProblemJson() {
        tokenRequest(GatewayTestTokens.supervisor(), "/api/v1/containers/CTN-000125")
                .expectStatus().isEqualTo(403)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:smartportx:problems:forbidden")
                .jsonPath("$.code").isEqualTo("forbidden");
    }

    @Test
    void readonlyRoleIsForbiddenOnContainerRoute() {
        tokenRequest(GatewayTestTokens.reader(), "/api/v1/containers/CTN-000126")
                .expectStatus().isEqualTo(403)
                .expectBody().jsonPath("$.code").isEqualTo("forbidden");
    }

    @Test
    void validRefreshTokenIsRejectedOnProtectedRoute() {
        tokenRequest(GatewayTestTokens.refreshToken(), "/api/v1/containers/CTN-000132")
                .expectStatus().isEqualTo(401)
                .expectBody().jsonPath("$.code").isEqualTo("unauthorized");
    }

    @Test
    void rolelessJwtIsDeniedOnRoleRestrictedRoute() {
        tokenRequest(GatewayTestTokens.roleless(), "/api/v1/containers/CTN-000133")
                .expectStatus().isEqualTo(403)
                .expectBody().jsonPath("$.code").isEqualTo("forbidden");
    }

    @Test
    void missingTokenYields401ProblemJson() {
        client().get().uri("/api/v1/containers/CTN-000127")
                .exchange()
                .expectStatus().isEqualTo(401)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:smartportx:problems:unauthorized")
                .jsonPath("$.code").isEqualTo("unauthorized")
                .jsonPath("$.correlationId").exists();
    }

    @Test
    void malformedTokenYields401ProblemJson() {
        client().get().uri("/api/v1/containers/CTN-000128")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                .exchange()
                .expectStatus().isEqualTo(401)
                .expectBody().jsonPath("$.code").isEqualTo("unauthorized");
    }

    @Test
    void correlationIdIsEchoedOnResponseHeaderAndInProblemBody() {
        client().get().uri("/api/v1/containers/CTN-000129")
                .header("X-Correlation-Id", "corr-123456")
                .exchange()
                .expectStatus().isEqualTo(401)
                .expectHeader().valueEquals("X-Correlation-Id", "corr-123456")
                .expectBody().jsonPath("$.correlationId").isEqualTo("corr-123456");
    }

    @Test
    void correlatedAuthenticatedRequestReachesUpstreamWithSameCorrelationId() {
        client().get().uri("/api/v1/containers/CTN-000130")
                .header("X-Correlation-Id", "corr-654321")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + GatewayTestTokens.terminalOp())
                .exchange()
                .expectStatus().isOk();
        assertThat(stub.lastCorrelationId()).isEqualTo("corr-654321");
    }

    @Test
    void terminalOperatorIsPermittedOnContainerRoute() {
        tokenRequest(GatewayTestTokens.terminalOp(), "/api/v1/containers/CTN-000131")
                .expectStatus().isOk()
                .expectBody().jsonPath("$.xUserRole").isEqualTo("TERMINAL_OP");
    }

    @Test
    void actuatorHealthIsPublic() {
        client().get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    private ResponseSpec tokenRequest(String token, String uri) {
        return client().get().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange();
    }
}