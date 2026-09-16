package com.smartportx.vessel.service.http;

import com.sun.net.httpserver.HttpServer;
import com.smartportx.vessel.service.ContainerApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provider contract test for the container HTTP client using the in-process JDK
 * HTTP server (WireMock is not resolvable offline — same substitution as gate M6).
 */
class HttpContainerApiClientTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> lastStatusBody = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String containerJson = "{\"id\":\"99400000-0000-0000-0000-000000000001\","
                + "\"containerNumber\":\"MSKU1000001\",\"containerType\":\"DRY\","
                + "\"status\":\"ON_VESSEL\",\"weightKg\":21000.0,\"reefer\":false}";
        server.createContext("/api/v1/containers/number/MSKU1000001", exchange -> {
            byte[] bytes = containerJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.createContext("/api/v1/containers/number/NOTFOUND", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.createContext("/api/v1/containers/", exchange -> {
            if (exchange.getRequestMethod().equals("PUT")) {
                byte[] body = exchange.getRequestBody().readAllBytes();
                lastStatusBody.set(new String(body, StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            } else {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
            }
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void findsContainerByNumber() {
        HttpContainerApiClient client = new HttpContainerApiClient(
                "http://127.0.0.1:" + port + "", 2000, 3000);
        Optional<ContainerApiClient.ContainerInfo> found =
                client.findByContainerNumber("MSKU1000001");
        assertThat(found).isPresent();
        assertThat(found.get().containerNumber()).isEqualTo("MSKU1000001");
        assertThat(found.get().status()).isEqualTo("ON_VESSEL");
        assertThat(found.get().weightKg()).isEqualTo(21000.0);
    }

    @Test
    void returnsEmptyForNotFound() {
        HttpContainerApiClient client = new HttpContainerApiClient(
                "http://127.0.0.1:" + port + "", 2000, 3000);
        assertThat(client.findByContainerNumber("NOTFOUND")).isEmpty();
    }

    @Test
    void changeStatusSendsPutWithStatus() {
        HttpContainerApiClient client = new HttpContainerApiClient(
                "http://127.0.0.1:" + port + "", 2000, 3000);
        UUID id = UUID.fromString("99400000-0000-0000-0000-000000000001");
        client.changeStatus(id, "DISCHARGED", "vessel-X", "discharge");
        assertThat(lastStatusBody.get())
                .contains("\"status\":\"DISCHARGED\"")
                .contains("\"location\":\"vessel-X\"");
    }
}