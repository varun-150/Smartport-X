# ADR-009: Authentication — JWT with Refresh Token Rotation

**Status:** PROPOSED
**Date:** 2026-08-26
**Deciders:** Architect, Lead

---

## Context

The SmartPort platform requires authentication and authorization across multiple services. Users (port operators, vessel managers, compliance officers) must:

1. **Authenticate** — prove their identity via credentials.
2. **Authorize** — access only the resources their role permits.
3. **Maintain sessions** — stay logged in across multiple page loads.
4. **Scale** — authentication must work across 7+ services without session synchronization.

Key constraints:
- Services are independent (ADR-001) — no shared session store is practical.
- Spring Cloud Gateway (ADR-006) must validate tokens centrally.
- The system must support role-based access control (RBAC) with at least 4 roles.
- Token revocation must be possible for security incidents.
- The frontend (ADR-008) must handle token refresh transparently.

## Decision

We adopt **JWT (RS256) with Refresh Token Rotation** as the authentication mechanism.

### Token Architecture

```
┌──────────────┐         ┌──────────────┐         ┌──────────────────┐
│  React UI    │──POST──→│  auth-service │──验证──→│  PostgreSQL       │
│  (login)     │         │  /api/auth    │         │  (auth.users)    │
└──────┬───────┘         └──────┬───────┘         └──────────────────┘
       │                        │
       │  ← Access Token (15min)│
       │  ← Refresh Token (7d)  │
       │                        │
       │  GET /api/vessels      │
       │  Authorization: Bearer │
       ▼                        ▼
┌──────────────┐         ┌──────────────┐
│  Gateway      │──验证──→│  vessel-service│
│  (JWT filter) │  RS256  │  (trusted)    │
└──────────────┘         └──────────────┘
```

### Token Details

| Property | Access Token | Refresh Token |
|----------|-------------|---------------|
| Algorithm | RS256 (RSA + SHA-256) | RS256 |
| Expiry | 15 minutes | 7 days |
| Storage | In-memory (React state) | HttpOnly Secure Cookie |
| Contents | userId, roles, permissions | userId, token family |
| Validation | Gateway + services | auth-service only |
| Rotation | On expiry | On each use (rotation) |

### JWT Payload

```json
{
  "sub": "user-uuid-1234",
  "iss": "smartport-auth",
  "iat": 1693036800,
  "exp": 1693037700,
  "roles": ["PORT_OPERATOR"],
  "permissions": ["vessel:read", "vessel:write", "berth:read"],
  "email": "operator@smartport.com"
}
```

### Key Rotation

```java
@Service
public class JwtTokenService {

    private final RSAKey signingKey;      // Current signing key
    private final RSAKey verificationKey; // Current verification key
    private final RSAKey previousKey;     // Previous key (for graceful rotation)

    public String generateAccessToken(UserDetails user) {
        return Jwts.builder()
            .subject(user.getId())
            .issuer("smartport-auth")
            .claim("roles", user.getRoles())
            .claim("permissions", user.getPermissions())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 15 * 60 * 1000))
            .signWith(signingKey, Jwts.SIG.RS256)
            .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
            .verifyWith(verificationKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
```

### Refresh Token Rotation

```java
@Service
public class RefreshTokenService {

    public TokenPair rotateRefreshToken(String oldRefreshToken) {
        RefreshToken oldToken = tokenRepository.findByToken(oldRefreshToken)
            .orElseThrow(() -> new InvalidTokenException("Token not found"));

        // Check if token was already used (token reuse detection)
        if (oldToken.isUsed()) {
            // Potential token theft — invalidate entire token family
            tokenRepository.invalidateFamily(oldToken.getTokenFamily());
            throw new SecurityException("Refresh token reuse detected");
        }

        // Mark old token as used
        oldToken.markUsed();

        // Generate new token pair
        RefreshToken newRefreshToken = generateRefreshToken(
            oldToken.getUserId(),
            oldToken.getTokenFamily()
        );

        TokenPair tokens = generateTokenPair(oldToken.getUserId());
        tokenRepository.save(newRefreshToken);

        return tokens;
    }
}
```

### RBAC Role Hierarchy

```java
public enum Role {
    SUPER_ADMIN("super_admin", List.of("*")),
    PORT_MANAGER("port_manager", List.of(
        "vessel:*", "berth:*", "cargo:*", "compliance:read",
        "analytics:read", "notification:*"
    )),
    PORT_OPERATOR("port_operator", List.of(
        "vessel:read", "vessel:write", "berth:read", "berth:write",
        "cargo:read", "cargo:write", "notification:read"
    )),
    COMPLIANCE_OFFICER("compliance_officer", List.of(
        "vessel:read", "compliance:*", "analytics:read"
    )),
    VIEWER("viewer", List.of(
        "vessel:read", "berth:read", "cargo:read", "analytics:read"
    ));
}
```

### Gateway JWT Validation Filter

```java
@Component
public class JwtGatewayFilter implements GatewayFilter, Ordered {

    private final JwtDecoder jwtDecoder;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        try {
            Jwt jwt = jwtDecoder.decode(token);

            // Check token expiry
            if (jwt.getExpiresAt().isBefore(Instant.now())) {
                return unauthorized(exchange, "Token expired");
            }

            // Propagate user info to downstream services
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", jwt.getSubject())
                .header("X-User-Roles", String.join(",", jwt.getClaimAsStringList("roles")))
                .header("X-User-Permissions", String.join(",", jwt.getClaimAsStringList("permissions")))
                .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (JwtException e) {
            return unauthorized(exchange, "Invalid token: " + e.getMessage());
        }
    }

    @Override
    public int getOrder() { return -10; }
}
```

## Alternatives Considered

### 1. Session-Based Authentication
Server-side sessions with HTTP-only cookies and a shared session store (Redis).
- **Pros:** Simple to revoke sessions, no token size limits, server controls session lifetime.
- **Cons:** Requires shared session store (Redis) across services, stateful (violates stateless services), session synchronization overhead, doesn't scale horizontally without sticky sessions.
- **Verdict:** Rejected — requires shared state, violating service autonomy.

### 2. OAuth 2.0 with External Provider (Keycloak/Auth0)
Using an external identity provider for authentication.
- **Pros:** Industry standard, handles MFA, social login, centralized user management.
- **Cons:** Additional infrastructure (Keycloak container), complex setup, overkill for internal port management system, adds another service to maintain.
- **Verdict:** Rejected for v1 — can be added later if external authentication is needed.

### 3. API Keys Only
Static API keys for service-to-service authentication.
- **Pros:** Simple to implement, no token refresh needed.
- **Cons:** No user-level authentication, keys must be manually rotated, no expiry, no RBAC granularity, security risk if keys leak.
- **Verdict:** Rejected — doesn't provide user-level authentication or RBAC.

### 4. HMAC-Based Tokens
Symmetric signing with a shared secret.
- **Pros:** Faster than RSA, simpler implementation.
- **Cons:** Shared secret must be distributed to all services, can't verify token origin, no asymmetric verification (any service can forge tokens).
- **Verdict:** Rejected — RS256 provides asymmetric verification where only auth-service can sign tokens.

### 5. Opaque Tokens with Introspection
Random tokens validated by calling the auth-service.
- **Pros:** Immediate revocation, no token size limits.
- **Cons:** Requires a network call to auth-service for every request (latency), auth-service becomes a bottleneck, doesn't scale.
- **Verdict:** Rejected — defeats the purpose of stateless JWT validation.

## Rationale

JWT with refresh token rotation is the optimal choice based on:

1. **Stateless validation:** Services validate JWTs locally using the public key — no network calls to auth-service for every request.
2. **Centralized signing:** Only auth-service holds the private key, ensuring only authorized token creation.
3. **Gateway-level validation:** Spring Cloud Gateway validates tokens once, reducing redundant validation across services.
4. **Refresh token rotation:** Provides security against token theft — if a refresh token is used twice, the entire token family is invalidated.
5. **RBAC support:** JWT claims carry role and permission information, enabling fine-grained access control.
6. **Scalability:** Stateless tokens scale horizontally without shared state.
7. **Industry standard:** JWT is the most widely adopted token format for microservices.
8. **Spring Security 6.x:** Provides excellent JWT support with `JwtDecoder`, `JwtAuthenticationConverter`, and method-level security annotations.

### Security Measures

| Measure | Implementation |
|---------|---------------|
| Algorithm | RS256 (RSA 2048-bit) |
| Token expiry | Access: 15min, Refresh: 7d |
| Token rotation | Refresh token rotated on each use |
| Reuse detection | Token family invalidation on reuse |
| Secure storage | Refresh token in HttpOnly Secure cookie |
| CORS | Restricted to frontend origin |
| Rate limiting | Login endpoint rate-limited (5 attempts/min) |
| Key rotation | RSA key pair rotated quarterly |

## Consequences

### Positive
- **Stateless scaling** — services validate tokens locally, no shared session store needed.
- **Gateway-level validation** — tokens validated once at the gateway, not per service.
- **RBAC support** — fine-grained permissions in JWT claims.
- **Refresh token rotation** — protects against token theft with family invalidation.
- **Asymmetric signing** — only auth-service can create valid tokens (RS256).
- **Cross-service propagation** — user context passed via headers to downstream services.

### Negative
- **Token revocation complexity** — JWTs cannot be revoked before expiry (mitigated by short 15-minute expiry and refresh token rotation).
- **Short-lived tokens** — users must refresh tokens every 15 minutes (mitigated by transparent refresh in the frontend).
- **Token size** — JWTs with roles/permissions can be 1-2KB (mitigated by keeping claims minimal).
- **Key management** — RSA key pairs must be securely stored and rotated (mitigated by Spring Security's key management).
- **No server-side session control** — admin cannot force-logout a user without blacklisting (mitigated by refresh token family invalidation).

## Related

- ADR-001: Architecture Style — SBA (stateless auth for independent services)
- ADR-003: Backend Framework — Spring Boot 3.x (Spring Security 6.x)
- ADR-006: API Gateway — Spring Cloud Gateway (JWT filter)
- ADR-008: Frontend — React (token storage and refresh handling)
- RFC 7519: JSON Web Token (JWT)
- RFC 7517: JSON Web Key (JWK)
