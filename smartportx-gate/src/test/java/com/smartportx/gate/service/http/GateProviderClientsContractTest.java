package com.smartportx.gate.service.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartportx.gate.service.ContainerClient;
import com.smartportx.gate.service.CustomsClient;
import com.smartportx.gate.service.YardClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tunnel contract tests for the gate's outbound partner-service clients. Stubs
 * each provider on an in-process JDK HTTP server so the assertions lock the
 * real network path (URI shape, verb, JSON decoding) and the fail-closed
 * discipline the domain relies on (see service client docs and API-CONTRACTS §6):
 * blocked/unreachable providers resolve to {@link Optional#empty()} except where
 * the contract mandates a released ({@code yard 404 = false}) or pending
 * ({@code customs 404 / null status = PENDING}) interpretation. WireMock was the
 * original plan but is not resolvable in this offline environment; this exercises
 * the same contract over real HTTP with zero additional dependencies.
 */
class GateProviderClientsContractTest {

    private static final String CN = "MSKU1234567";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentLinkedQueue<String> requestedPaths = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> acceptHeaders = new ConcurrentLinkedQueue<>();
    private HttpServer server;

    @BeforeEach
    void startProvider() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stopProvider() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void containerClient_decodes200IntoContract() {
        HttpContainerClient client = new HttpContainerClient(baseUrl(), 2000, 3000, objectMapper);
        Optional<ContainerClient.ContainerInfo> result = client.findByContainerNumber(CN);
        assertThat(result).isPresent();
        assertThat(result.get().containerNumber()).isEqualTo(CN);
        assertThat(result.get().containerType()).isEqualTo("DRY");
        assertThat(result.get().status()).isEqualTo("IN_YARD");
        assertThat(result.get().weightKg()).isEqualTo(21450.5);
        assertThat(result.get().dangerousGoods()).isFalse();
        assertThat(requestedPaths).contains("/api/v1/containers/number/" + CN);
        assertThat(acceptHeaders).contains("application/json");
    }

    @Test
    void containerClient_nullStatusPassesThroughNull() {
        Optional<ContainerClient.ContainerInfo> result =
                new HttpContainerClient(baseUrl(), 2000, 3000, objectMapper).findByContainerNumber("NULL-X1");
        assertThat(result).isPresent();
        assertThat(result.get().status()).isNull();
    }

    @Test
    void containerClient_404IsFailClosedEmpty() {
        assertThat(new HttpContainerClient(baseUrl(), 2000, 3000, objectMapper)
                .findByContainerNumber("404-X1")).isEmpty();
    }

    @Test
    void containerClient_5xxIsFailClosedEmpty() {
        assertThat(new HttpContainerClient(baseUrl(), 2000, 3000, objectMapper)
                .findByContainerNumber("500-X1")).isEmpty();
    }

    @Test
    void containerClient_unreachableProviderFailsClosed() throws IOException {
        assertThat(new HttpContainerClient(closedPortBaseUrl(), 500, 500, objectMapper)
                .findByContainerNumber(CN)).isEmpty();
    }

    @Test
    void yardClient_decodesAssignedAndReleased() {
        HttpYardClient client = new HttpYardClient(baseUrl(), 2000, 3000, objectMapper);
        assertThat(client.isPositionAssigned(CN)).contains(true);
        assertThat(client.isPositionAssigned("REL-X1")).contains(false);
        assertThat(requestedPaths).contains("/api/v1/yard/containers/" + CN + "/position");
    }

    @Test
    void yardClient_404MapsReleasedNotConfigured() {
        assertThat(new HttpYardClient(baseUrl(), 2000, 3000, objectMapper)
                .isPositionAssigned("404-X1")).contains(false);
    }

    @Test
    void yardClient_5xxIsFailClosedEmpty() {
        assertThat(new HttpYardClient(baseUrl(), 2000, 3000, objectMapper)
                .isPositionAssigned("500-X1")).isEmpty();
    }

    @Test
    void yardClient_unreachableProviderFailsClosed() throws IOException {
        assertThat(new HttpYardClient(closedPortBaseUrl(), 500, 500, objectMapper)
                .isPositionAssigned(CN)).isEmpty();
    }

    @Test
    void customsClient_decodesStatusAndNormalizesCase() {
        HttpCustomsClient client = new HttpCustomsClient(baseUrl(), 2000, 3000, objectMapper);
        assertThat(client.statusFor(CN).get().status()).isEqualTo("CLEARED");
        assertThat(client.statusFor(CN).get().isCleared()).isTrue();
        assertThat(client.statusFor("LOWER-X1").get().status()).isEqualTo("CLEARED");
    }

    @Test
    void customsClient_heldStatusBlocks() {
        assertThat(new HttpCustomsClient(baseUrl(), 2000, 3000, objectMapper)
                .statusFor("HELD-X1").get().isHeld()).isTrue();
    }

    @Test
    void customsClient_nullAndNotFoundStatusArePending() {
        HttpCustomsClient client = new HttpCustomsClient(baseUrl(), 2000, 3000, objectMapper);
        assertThat(client.statusFor("NULL-X1").get().status()).isEqualTo("PENDING");
        assertThat(client.statusFor("404-X1").get().status()).isEqualTo("PENDING");
    }

    @Test
    void customsClient_5xxIsFailClosedEmpty() {
        assertThat(new HttpCustomsClient(baseUrl(), 2000, 3000, objectMapper)
                .statusFor("500-X1")).isEmpty();
    }

    @Test
    void customsClient_unreachableProviderFailsClosed() throws IOException {
        assertThat(new HttpCustomsClient(closedPortBaseUrl(), 500, 500, objectMapper)
                .statusFor(CN)).isEmpty();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requestedPaths.add(path);
        acceptHeaders.add(exchange.getRequestHeaders().getFirst("Accept"));
        byte[] body;
        int status;
        if (path.startsWith("/api/v1/containers/number/")) {
            String cn = path.substring("/api/v1/containers/number/".length());
            if (cn.equals("404-X1")) {
                status = 404;
                body = new byte[0];
            } else if (cn.equals("500-X1")) {
                status = 500;
                body = "boom".getBytes(StandardCharsets.UTF_8);
            } else {
                status = 200;
                body = containerJson(cn, cn.equals("NULL-X1") ? null : "IN_YARD").getBytes(StandardCharsets.UTF_8);
            }
        } else if (path.startsWith("/api/v1/yard/containers/") && path.endsWith("/position")) {
            String cn = path.substring("/api/v1/yard/containers/".length(),
                    path.length() - "/position".length());
            if (cn.equals("404-X1")) {
                status = 404;
                body = new byte[0];
            } else if (cn.equals("500-X1")) {
                status = 500;
                body = "boom".getBytes(StandardCharsets.UTF_8);
            } else {
                status = 200;
                body = yardJson(!cn.equals("REL-X1"), cn.equals("REL-X1") ? null : "A-03-02")
                        .getBytes(StandardCharsets.UTF_8);
            }
        } else if (path.startsWith("/api/v1/customs/containers/") && path.endsWith("/control")) {
            String cn = path.substring("/api/v1/customs/containers/".length(),
                    path.length() - "/control".length());
            if (cn.equals("404-X1")) {
                status = 404;
                body = new byte[0];
            } else if (cn.equals("500-X1")) {
                status = 500;
                body = "boom".getBytes(StandardCharsets.UTF_8);
            } else {
                String value = switch (cn) {
                    case "HELD-X1" -> "HELD";
                    case "LOWER-X1" -> "cleared";
                    case "NULL-X1" -> null;
                    default -> "CLEARED";
                };
                status = 200;
                body = customsJson(cn, value).getBytes(StandardCharsets.UTF_8);
            }
        } else {
            status = 404;
            body = new byte[0];
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String containerJson(String cn, String status) {
        return "{\"id\":\"3f2d1a2b-1111-4e8a-9c00-000000000001\",\"containerNumber\":\"" + cn
                + "\",\"containerType\":\"DRY\",\"status\":"
                + (status == null ? "null" : "\"" + status + "\"")
                + ",\"weight\":21450.5,\"dangerousGoods\":false}";
    }

    private static String yardJson(boolean assigned, String position) {
        return "{\"assigned\":" + assigned + ",\"position\":"
                + (position == null ? "null" : "\"" + position + "\"") + "}";
    }

    private static String customsJson(String cn, String status) {
        return "{\"containerNumber\":\"" + cn + "\",\"status\":"
                + (status == null ? "null" : "\"" + status + "\"")
                + ",\"containerType\":\"DRY\"}";
    }

    private static String closedPortBaseUrl() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return "http://127.0.0.1:" + socket.getLocalPort();
        }
    }
}