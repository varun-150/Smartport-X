# SMARTPORTX — SECURITY TEST REPORT (Phase 4)

> Status: **NOT YET RUN** — stub created at Phase 4 kickoff (2026-08-27). Populated continuously as each
> `XSecurityTest` class lands (TEST-STRATEGY §7) and completed at M12.4. Security agent sign-off is
> BLOCKING (brief §35). All 36 SR mapped or explicitly deferred.

--------------------------------------------------------------------------------
## 1. COVERAGE MAP (36 SR)
| SR | Control | Test | Status | Evidence |
|----|---------|------|--------|----------|
| SR-01 authN (registration) | M3.3/8 | `IdentitySecurityTest.reg_*` | NOT RUN | — |
| SR-02 authN (login+lockout) | M3.4/8 | rate-limit/lockout | NOT RUN | — |
| SR-03 token integrity (alg/key) | M3.5/8 | forged token attack | NOT RUN | — |
| SR-04 refresh rotation/reuse | M3.5/8 | reuse→revoke family | NOT RUN | — |
| SR-05 password policy/history | M3.3/8 | policy cases | NOT RUN | — |
| SR-06 RBAC method-level | all slices | per-role matrix | NOT RUN | — |
| SR-07 gateway route authn | M10.4 | whitelist/inject | NOT RUN | — |
| SR-08 gateway route authz (role-route) | M10.4 | deny matrix | NOT RUN | — |
| SR-09 IDOR / BOLA | M3–M9 | cross-user access | NOT RUN | — |
| SR-10 input validation/SQLi | all slices | attack payloads | NOT RUN | — |
| SR-11 XSS (dashboard) | M11.9 | sanitization/Playwright | NOT RUN | — |
| SR-12 CSRF | N/A (token-based) | documented rationale | NOT RUN | — |
| SR-13 CORS lock | gateway | origin allowlist | NOT RUN | — |
| SR-14 error-id no info leak | M2.1 | ProblemDetail leak test | NOT RUN | — |
| SR-15 secrets in repo | CI | gitleaks scan | NOT RUN | — |
| SR-16 auditability | M3/M9 | audit row written+assert | NOT RUN | — |
| SR-17 rate limiting | gateway+auth | 429 paths | NOT RUN | — |
| SR-18 detection/alerting (WS alerts) | M9.4 | alert emission test | NOT RUN | — |
| SR-19..36 | remaining (SECURITY-REQUIREMENTS.md §) | mapped in full table at M12.4 | NOT RUN | — |

## 2. ATTACK MATRIX (run at M12.4)
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