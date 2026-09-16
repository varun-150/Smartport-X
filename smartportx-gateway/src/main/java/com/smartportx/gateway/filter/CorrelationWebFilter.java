package com.smartportx.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive mirror of {@code com.smartportx.common.correlation.CorrelationIdFilter}
 * (the gateway must not depend on smartportx-common). Assigns or propagates
 * {@code X-Correlation-Id}, echoes it on the response header and forwards it
 * downstream so services share one correlation id per request.
 */
@Component
public class CorrelationWebFilter implements WebFilter, Ordered {

    public static final String HEADER = "X-Correlation-Id";
    public static final String ATTRIBUTE = CorrelationWebFilter.class.getName() + ".CORRELATION_ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String id = (incoming != null && !incoming.isBlank()) ? incoming : UUID.randomUUID().toString();
        exchange.getAttributes().put(ATTRIBUTE, id);
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> headers.set(HEADER, id))
                .build();
        exchange.getResponse().getHeaders().set(HEADER, id);
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return -1000;
    }
}