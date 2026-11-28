package com.smartportx.identity.security;

import com.smartportx.identity.config.SecurityProperties;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class LoginRateLimiter {

    private final RateLimiterRegistry registry;
    private final RateLimiterConfig config;

    public LoginRateLimiter(SecurityProperties properties) {
        this.registry = RateLimiterRegistry.ofDefaults();
        this.config = RateLimiterConfig.custom()
                .limitForPeriod(properties.loginRateLimit())
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build();
    }

    public boolean tryAcquire(String key) {
        RateLimiter limiter = registry.rateLimiter(key, config);
        return limiter.acquirePermission();
    }
}