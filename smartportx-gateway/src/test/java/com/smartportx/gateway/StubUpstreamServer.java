package com.smartportx.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Minimal JDK {@link HttpServer} stub standing in for upstream services in gateway
 * contract tests (no extra test dependencies, no bound ports besides the one we pick).
 *
 * <ul>
 *   <li>{@code GET /api/v1/auth/login}       {@literal ->} 200 JSON (public route check)</li>
 *   <li>{@code GET /api/v1/containers/...}   {@literal ->} 200 echoing received headers</li>
 *   <li>{@code GET /slow}                    {@literal ->} 200 after a 2s delay (timeout test)</li>
 * </ul>
 */
public final class StubUpstreamServer implements AutoCloseable {

    private final HttpServer server;
    private volatile String lastPath;
    private volatile String lastAuthorization;
    private volatile String lastXUserId;
    private volatile String lastXUserRole;
    private volatile String lastCorrelationId;

    private StubUpstreamServer(HttpServer server) {
        this.server = server;
    }

    public static StubUpstreamServer start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        StubUpstreamServer stub = new StubUpstreamServer(server);
        server.createContext("/", stub::handle);
        server.start();
        return stub;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String lastPath() {
        return lastPath;
    }

    public String lastAuthorization() {
        return lastAuthorization;
    }

    public String lastXUserId() {
        return lastXUserId;
    }

    public String lastXUserRole() {
        return lastXUserRole;
    }

    public String lastCorrelationId() {
        return lastCorrelationId;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        lastPath = path;
        lastAuthorization = header(exchange, "Authorization");
        lastXUserId = header(exchange, "X-User-Id");
        lastXUserRole = header(exchange, "X-User-Role");
        lastCorrelationId = header(exchange, "X-Correlation-Id");

        byte[] body;
        int status = 200;
        if (path.endsWith("/slow")) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        } else {
            body = ("{\"path\":\"" + path + "\",\"xUserId\":\"" + nvl(lastXUserId)
                    + "\",\"xUserRole\":\"" + nvl(lastXUserRole)
                    + "\",\"correlationId\":\"" + nvl(lastCorrelationId)
                    + "\",\"authorizationPresent\":\"" + (lastAuthorization != null)
                    + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static String header(HttpExchange exchange, String name) {
        var values = exchange.getRequestHeaders().get(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}