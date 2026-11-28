package com.smartportx.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "smartportx.gateway")
public record SmartPortxGatewayProperties(
        Authorization authorization,
        RateLimit rateLimit) {

    public SmartPortxGatewayProperties {
        if (authorization == null) {
            authorization = new Authorization(List.of(), List.of());
        }
        if (rateLimit == null) {
            rateLimit = new RateLimit(100, 60);
        }
    }

    public record Authorization(List<String> publicRoutes, List<RoleRoute> roleRoutes) {
        public Authorization {
            if (publicRoutes == null) {
                publicRoutes = new ArrayList<>();
            }
            if (roleRoutes == null) {
                roleRoutes = new ArrayList<>();
            }
        }
    }

    public record RoleRoute(String path, List<String> roles) {
        public RoleRoute {
            if (roles == null) {
                roles = new ArrayList<>();
            }
        }
    }

    public record RateLimit(int capacity, int refillPerMinute) {
        public RateLimit {
            if (capacity <= 0) {
                capacity = 100;
            }
            if (refillPerMinute <= 0) {
                refillPerMinute = 60;
            }
        }
    }
}