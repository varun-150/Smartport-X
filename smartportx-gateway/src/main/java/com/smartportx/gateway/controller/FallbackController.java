package com.smartportx.gateway.controller;

import com.smartportx.gateway.error.GatewayProblems;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Circuit-breaker fallback endpoint. A route whose CircuitBreaker filter trips (dead
 * service, timeout, circuit open) forwards to {@code /fallback/{service}}. The filter
 * stores the triggering exception under {@link
 * ServerWebExchangeUtils#CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR}; an upstream timeout is
 * reported as 504, everything else as 503, both as problem+json.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/{service}")
    public Mono<Void> fallback(@PathVariable String service, ServerWebExchange exchange) {
        Throwable ex = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        if (GatewayProblems.isTimeout(ex)) {
            return GatewayProblems.write(exchange, HttpStatus.GATEWAY_TIMEOUT, "gateway_timeout",
                    "Upstream service did not respond within the gateway timeout window");
        }
        return GatewayProblems.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "service_unavailable",
                "Service '" + service + "' is unavailable; request was not processed");
    }
}