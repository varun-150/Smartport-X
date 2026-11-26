# SMARTPORTX — SECURITY TEST REPORT (Phase 4)

> Status: **PARTIAL AUTOMATED COVERAGE — NOT SECURITY SIGN-OFF**. The backend CI suite passes, and
> existing integration tests cover selected authentication, refresh-token, gateway authorization,
> rate-limit, Eureka access, and WebSocket authorization paths. The full 36-requirement attack matrix
> has not been completed; security reviewer sign-off remains BLOCKING (brief §35).

--------------------------------------------------------------------------------
## 1. COVERAGE MAP (36 SR)
| SR | Control | Test | Status | Evidence |
|----|---------|------|--------|----------|
| SR-01 authN (registration/login) | M3.3/8 | `AuthFlowIntegrationTest`, `AdminUserManagementIntegrationTest` | PARTIAL — registration-specific cases still missing | CI backend suite |
| SR-02 authN (login+lockout) | M3.4/8 | `LoginSecurityIntegrationTest`, `RateLimitIntegrationTest` | PARTIAL — lockout/rate limit covered; broader brute-force cases missing | CI backend suite |
| SR-03 token integrity (alg/key) | M3.5/8 | `LoginSecurityIntegrationTest`, `GatewayEdgeContractTest` | PARTIAL — tampered/roleless/refresh tokens covered; algorithm-confusion and foreign-key cases missing | CI backend suite |
| SR-04 refresh rotation/reuse | M3.5/8 | `AuthFlowIntegrationTest` | PARTIAL — rotation, reuse rejection, logout revocation covered; complete family-state assertions still needed | CI backend suite |
| SR-05 password policy/history | M3.3/8 | `AuthFlowIntegrationTest` | PARTIAL — policy and password change covered; history depth/matrix not fully verified | CI backend suite |
| SR-06 RBAC method-level | all slices | `AdminUserManagementIntegrationTest`, `GatewayEdgeContractTest`, `TopicAuthorizationServiceTest` | PARTIAL — selected roles/routes/topics covered; complete service-method matrix missing | CI backend suite |
| SR-07 gateway route authn | M10.4 | `GatewayEdgeContractTest` | PARTIAL — missing/malformed/refresh tokens and public-route bypass covered | CI backend suite |
| SR-08 gateway route authz (role-route) | M10.4 | `GatewayEdgeContractTest` | PARTIAL — selected allow/deny and trusted-header cases covered; complete route matrix missing | CI backend suite |
| SR-09 IDOR / BOLA | M3–M9 | cross-user resource access tests | NOT RUN | — |
| SR-10 input validation/SQLi | all slices | controller validation tests | PARTIAL — validation covered; SQL injection payload matrix missing | CI backend suite |
| SR-11 XSS (dashboard) | M11.9 | sanitization/Playwright attack cases | NOT RUN | — |
| SR-12 CSRF | N/A (token-based) | documented rationale and browser-origin validation | PARTIAL — rationale exists; browser-origin/CSRF assumptions need verification | — |
| SR-13 CORS lock | gateway | allowed/denied origin matrix | NOT RUN | — |
| SR-14 error-id no info leak | M2.1 | `GatewayEdgeContractTest` | PARTIAL — problem responses asserted; sensitive-data leakage cases missing | CI backend suite |
| SR-15 secrets in repo | CI | Gitleaks | PASS — scan completed successfully; this does not replace secret rotation review | CI Gitleaks job |
| SR-16 auditability | M3/M9 | audit row written+assert | NOT RUN | — |
| SR-17 rate limiting | gateway+auth | `GatewayRateLimitTest`, `RateLimitIntegrationTest` | PASS — configured 429 paths asserted | CI backend suite |
| SR-18 detection/alerting (WS alerts) | M9.4 | `StompWebSocketIntegrationTest` | PARTIAL — alert broadcast tested; detection/alerting pipeline and monitoring rules unverified | CI backend suite |
| SR-19..36 | remaining (SECURITY-REQUIREMENTS.md §) | mapped in full table at M12.4 | NOT RUN | — |

## 2. REMAINING ATTACK MATRIX (not yet run end-to-end)
- JWT: valid 15m / expired / nbf-future / malformed base64 / wrong alg (HS256 trick) / foreign key / replayed.
- Accel: role-swap attempt (GATE_OPERATOR→ADMIN claim injected directly) → 403 at gateway AND at method.
- Enumeration: login error text not leaking existence; refresh reuse chain kills family.
- Injection: SQLi on container_number filter, XSS payload on appointment plate number, oversized body.
- Secrets: scan output attached; 0 findings — CI gate.

## 3. RESULT TABLE
| Class | Service | Tests | Pass | Fail | Sign-off |
|-------|---------|-------|------|------|----------|
| IdentitySecurityTest | identity | — | — | — | — |
| ContainerSecurityTest | container | — | — | — | — |
| YardSecurityTest | yard | — | — | — | — |
| GateSecurityTest | gate | — | — | — | — |
| VesselSecurityTest | vessel | — | — | — | — |
| IntegrationSecurityTest | integration | — | — | — | — |
| NotificationSecurityTest | notification | — | — | — | — |
| GatewaySecurityTest | gateway | — | — | — | — |

## 4. SIGN-OFF
Security agent: _(name)_ — BLOCKING, no closure without signature. BLOCKERS: R-007 (broker) for WS tests.