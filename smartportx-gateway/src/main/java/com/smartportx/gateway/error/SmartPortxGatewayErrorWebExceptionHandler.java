package com.smartportx.gateway.error;

import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Replacements for Spring Cloud Gateway's default error response, so failures surface as
 * RFC 9457 problem+json bodies instead of the opaque HTML error page. Mapped cases:
 * {@link ResponseStatusException} (status from the exception), upstream
 * {@link TimeoutException}/{@link SocketTimeoutException} & the gateway's
 * {@code ResponseTimeoutException} (504), everything else (500).
 */
@Component
@Order(-2)
public class SmartPortxGatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        exchange.getResponse().getHeaders().remove(org.springframework.http.HttpHeaders.TRANSFER_ENCODING);
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ResponseStatusException statusException) {
                HttpStatus status = HttpStatus.resolve(statusException.getStatusCode().value());
                if (status == null) {
                    status = HttpStatus.INTERNAL_SERVER_ERROR;
                }
                return GatewayProblems.write(exchange, status, code(status),
                        statusException.getReason() != null ? statusException.getReason() : status.getReasonPhrase());
            }
            if (GatewayProblems.isTimeout(cause)) {
                return GatewayProblems.write(exchange, HttpStatus.GATEWAY_TIMEOUT, "gateway_timeout",
                        "Upstream service did not respond within the gateway timeout window");
            }
        }
        return GatewayProblems.write(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                "An unexpected gateway error occurred");
    }

    private String code(HttpStatus status) {
        return switch (status.value()) {
            case 400 -> "bad_request";
            case 401 -> "unauthorized";
            case 403 -> "forbidden";
            case 404 -> "not_found";
            case 408 -> "request_timeout";
            case 429 -> "rate_limited";
            case 503 -> "service_unavailable";
            case 504 -> "gateway_timeout";
            default -> "error";
        };
    }
}