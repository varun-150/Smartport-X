package com.smartportx.gateway.filter;

import com.smartportx.gateway.config.SmartPortxGatewayProperties;
import com.smartportx.gateway.error.GatewayProblems;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory token-bucket rate limiter. A bucket is keyed by <em>route id + user</em>
 * (from the trusted {@code X-User-Id} injected by {@link JwtEdgeFilter}) and falls back
 * to the client IP for unauthenticated/public traffic. {@code /ws/**} and actuator
 * liveness endpoints are exempt; OPTIONS pass through. A violated limit yields 429 with
 * a {@code Retry-After} header.
 *
 * <p>Note: per-instance only — a multi-replica deployment must move this policy out of
 * process (e.g. Redis). Documented as an intentional single-node demo limitation.
 */
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final Duration WINDOW = Duration.ofSeconds(1);
    private static final int DEFAULT_CAPACITY = 100;
    private static final int DEFAULT_REFILL_PER_MINUTE = 60;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final SmartPortxGatewayProperties properties;

    public RateLimitGlobalFilter(SmartPortxGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();
        if (HttpMethod.OPTIONS.equals(method)
                || path.startsWith("/ws/")
                || path.startsWith("/actuator/health")
                || path.startsWith("/actuator/info")) {
            return chain.filter(exchange);
        }

        String key = rateLimitKey(exchange);
        SmartPortxGatewayProperties.RateLimit config = properties.rateLimit();
        int capacity = config == null ? DEFAULT_CAPACITY : config.capacity();
        int refillPerMinute = config == null ? DEFAULT_REFILL_PER_MINUTE : config.refillPerMinute();

        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(capacity, refillPerMinute));
        long waitMillis = bucket.tryAcquire();
        if (waitMillis > 0) {
            exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(waitMillis));
            return GatewayProblems.write(exchange, HttpStatus.TOO_MANY_REQUESTS, "rate_limited",
                    "Rate limit exceeded; retry after " + waitMillis + "s");
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private String rateLimitKey(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route == null ? "unknown" : route.getId();
        String userId = (String) exchange.getAttribute(JwtEdgeFilter.USER_ID_ATTR);
        if (userId != null) {
            return routeId + "|user|" + userId;
        }
        String ip = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
        return routeId + "|ip|" + ip;
    }

    /** Slow-refilling token bucket; returns seconds to wait when the limit is exceeded. */
    static final class Bucket {
        private final double capacity;
        private final double refillPerSecond;
        private double tokens;
        private long lastRefillNanos;

        Bucket(int capacity, int refillPerMinute) {
            this.capacity = capacity;
            this.refillPerSecond = refillPerMinute / 60.0;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized long tryAcquire() {
            long nowNanos = System.nanoTime();
            double elapsedSecs = (nowNanos - lastRefillNanos) / 1_000_000_000.0;
            tokens = Math.min(capacity, tokens + elapsedSecs * refillPerSecond);
            lastRefillNanos = nowNanos;
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return 0;
            }
            if (refillPerSecond <= 0) {
                return 60;
            }
            long waitSecs = (long) Math.ceil((1.0 - tokens) / refillPerSecond);
            return Math.max(1, Math.min(waitSecs, 60));
        }
    }
}