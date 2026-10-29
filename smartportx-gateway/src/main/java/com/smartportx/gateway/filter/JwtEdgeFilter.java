package com.smartportx.gateway.filter;

import com.smartportx.gateway.config.SmartPortxGatewayProperties;
import com.smartportx.gateway.error.GatewayProblems;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Edge JWT authorization for Spring Cloud Gateway. Validates RS256 tokens against the
 * identity service JWKS, enforces per-route role lists, strips untrusted client-supplied
 * {@code X-User-Id}/{@code X-User-Role} headers and injects trusted values from the token.
 * The original {@code Authorization} header is forwarded downstream unchanged.
 *
 * <p>Public routes (auth endpoints, health/info) pass through. Without a valid token a
 * 401 problem+json body is written; a valid token without the route's required role
 * yields 403.
 */
@Component
public class JwtEdgeFilter implements GlobalFilter, Ordered {

    static final String USER_ID_ATTR = JwtEdgeFilter.class.getName() + ".USER_ID";
    static final String USER_ROLE_ATTR = JwtEdgeFilter.class.getName() + ".USER_ROLE";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String X_USER_ID = "X-User-Id";
    private static final String X_USER_ROLE = "X-User-Role";

    private final ReactiveJwtDecoder jwtDecoder;
    private final SmartPortxGatewayProperties properties;

    public JwtEdgeFilter(ReactiveJwtDecoder jwtDecoder, SmartPortxGatewayProperties properties) {
        this.jwtDecoder = jwtDecoder;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        if (HttpMethod.OPTIONS.equals(method)
                || isWebSocketPath(path)
                || isPublicRoute(path)
                || isActuatorLiveness(path)) {
            return chain.filter(exchange);
        }

        String token = bearerToken(exchange.getRequest());
        if (token == null) {
            return GatewayProblems.write(exchange, HttpStatus.UNAUTHORIZED, "unauthorized",
                    "Missing or malformed Authorization header");
        }

        return jwtDecoder.decode(token)
                .flatMap(jwt -> authorize(exchange, chain, jwt))
                .onErrorResume(error -> error instanceof JwtException
                        ? GatewayProblems.write(exchange, HttpStatus.UNAUTHORIZED,
                                "unauthorized", "Invalid or expired token")
                        : Mono.error(error));
    }

    private Mono<Void> authorize(ServerWebExchange exchange, GatewayFilterChain chain, Jwt jwt) {
        // Refresh tokens are validly signed JWTs but are never API access credentials.
        if ("refresh".equals(jwt.getClaimAsString("type"))) {
            return GatewayProblems.write(exchange, HttpStatus.UNAUTHORIZED, "unauthorized",
                    "Access token required");
        }

        List<String> roles = jwt.getClaimAsStringList("roles");
        List<String> required = requiredRoles(exchange.getRequest().getPath().value());
        if (required != null && (roles == null || roles.stream().noneMatch(required::contains))) {
            return GatewayProblems.write(exchange, HttpStatus.FORBIDDEN, "forbidden",
                    "Insufficient role for requested resource");
        }

        String subject = jwt.getSubject();
        String role = (roles == null || roles.isEmpty()) ? null : roles.get(0);
        exchange.getAttributes().put(USER_ID_ATTR, subject);
        if (role != null) {
            exchange.getAttributes().put(USER_ROLE_ATTR, role);
        }

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(X_USER_ID);
                    headers.remove(X_USER_ROLE);
                    headers.set(X_USER_ID, subject);
                    if (role != null) {
                        headers.set(X_USER_ROLE, role);
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    static boolean isWebSocketPath(String path) {
        return "/ws".equals(path) || path.startsWith("/ws/");
    }

    private boolean isPublicRoute(String path) {
        List<String> publicRoutes = properties.authorization().publicRoutes();
        return publicRoutes != null && publicRoutes.contains(path);
    }

    private boolean isActuatorLiveness(String path) {
        return path.startsWith("/actuator/health") || path.startsWith("/actuator/info");
    }

    /** Longest matching role-route prefix, or {@code null} when the path is not role-gated. */
    private List<String> requiredRoles(String path) {
        List<SmartPortxGatewayProperties.RoleRoute> roleRoutes = properties.authorization().roleRoutes();
        if (roleRoutes == null) {
            return null;
        }
        String best = null;
        List<String> bestRoles = null;
        for (SmartPortxGatewayProperties.RoleRoute route : roleRoutes) {
            String routePath = route.path();
            if (path.equals(routePath) || (path.startsWith(routePath) && path.charAt(routePath.length()) == '/')) {
                if (best == null || routePath.length() > best.length()) {
                    best = routePath;
                    bestRoles = route.roles();
                }
            }
        }
        return bestRoles;
    }

    private String bearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    @Override
    public int getOrder() {
        return -100;
    }
}