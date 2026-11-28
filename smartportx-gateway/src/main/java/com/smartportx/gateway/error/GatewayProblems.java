package com.smartportx.gateway.error;

import com.smartportx.gateway.filter.CorrelationWebFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Writes RFC 9457 problem+json bodies mirroring {@code com.smartportx.common.error.ApiProblems}
 * (the gateway must not depend on smartportx-common): {@code type} =
 * {@code urn:smartportx:problems:<code>}, {@code title}, {@code status}, {@code code},
 * {@code detail}, {@code errorId} (server errors), {@code correlationId}, {@code instance}.
 * Server errors never leak exception detail.
 */
public final class GatewayProblems {

    private static final String PROBLEM_NS = "urn:smartportx:problems:";

    private GatewayProblems() {
    }

    /**
     * Whether {@code ex} (or a cause) is an upstream timeout the gateway must report as
     * 504. Matches the timeout family emitted by the gateway HTTP client (
     * {@code ResponseTimeoutException}, netty {@code ReadTimeoutException}),
     * {@link TimeoutException}, {@link SocketTimeoutException},
     * {@link org.springframework.cloud.gateway.support.TimeoutException}, plus any
     * {@link ResponseStatusException} already mapped to 504.
     */
    public static boolean isTimeout(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
            if (cause instanceof ResponseStatusException statusException
                    && statusException.getStatusCode() == HttpStatus.GATEWAY_TIMEOUT) {
                return true;
            }
            String simple = cause.getClass().getSimpleName();
            if ("ResponseTimeoutException".equals(simple) || "ReadTimeoutException".equals(simple)
                    || "TimeoutException".equals(simple)) {
                return true;
            }
        }
        return false;
    }

    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String detail) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", PROBLEM_NS + code);
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("code", code);
        body.put("detail", detail);
        Object correlationId = exchange.getAttribute(CorrelationWebFilter.ATTRIBUTE);
        if (correlationId != null) {
            body.put("correlationId", correlationId);
        }
        body.put("instance", exchange.getRequest().getPath().value());
        if (status.is5xxServerError()) {
            body.put("errorId", UUID.randomUUID().toString());
        }
        byte[] json = Json.write(body);
        DataBuffer buffer = response.bufferFactory().wrap(json);
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().setContentLength(json.length);
        return response.writeWith(Mono.just(buffer)).then(Mono.empty());
    }
}