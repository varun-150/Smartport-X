# SECURITY REQUIREMENTS — SmartPortX

## Threat Model

### STRIDE Analysis for SmartPortX

| Threat Category | Applicable Threats | Risk Level |
|---|---|---|
| **Spoofing** | Fake container IDs, unauthorized API access, impersonated truck drivers | HIGH |
| **Tampering** | Container data modification, audit log tampering, sensor data manipulation | HIGH |
| **Repudiation** | Actions performed without audit trail, denial of container receipt | MEDIUM |
| **Information Disclosure** | Customs data leakage, commercial data exposure, container location exposure | HIGH |
| **Denial of Service** | API overload, database exhaustion, optimization service flooding | MEDIUM |
| **Elevation of Privilege** | Role escalation, unauthorized yard modification, gate bypass | HIGH |

## Security Requirements by Category

### SR-001: Identity and Access Management
| ID | Requirement | Priority |
|---|---|---|
| SR-001.1 | All users must authenticate before accessing the system | CRITICAL |
| SR-001.2 | Authentication uses JWT tokens with short expiry (15 min access, 7 day refresh) | CRITICAL |
| SR-001.3 | Role-based access control enforced on all API endpoints | CRITICAL |
| SR-001.4 | Service-to-service authentication uses API keys + mTLS | HIGH |
| SR-001.5 | Failed login attempts limited to 5 per 15-minute window | HIGH |
| SR-001.6 | Account lockout after 10 consecutive failed attempts | HIGH |
| SR-001.7 | Password policy: minimum 12 characters, complexity requirements | HIGH |

### SR-002: Data Protection
| ID | Requirement | Priority |
|---|---|---|
| SR-002.1 | All data in transit encrypted with TLS 1.3 | CRITICAL |
| SR-002.2 | Sensitive fields (customs data, seal numbers) encrypted at rest | HIGH |
| SR-002.3 | Container location data accessible only to authorized roles | HIGH |
| SR-002.4 | No sensitive data in URL parameters or log messages | CRITICAL |
| SR-002.5 | API responses filtered by role (no data leakage across tenants) | CRITICAL |

### SR-003: Input Validation and Injection Prevention
| ID | Requirement | Priority |
|---|---|---|
| SR-003.1 | All user inputs validated server-side before processing | CRITICAL |
| SR-003.2 | Parameterized queries used exclusively (no string concatenation) | CRITICAL |
| SR-003.3 | Container IDs validated against ISO 6346 format | HIGH |
| SR-003.4 | File uploads (EDIFACT messages) scanned and size-limited | HIGH |
| SR-003.5 | JSON request body size limited to 1MB | MEDIUM |

### SR-004: Audit and Logging
| ID | Requirement | Priority |
|---|---|---|
| SR-004.1 | All state-changing operations logged with timestamp, user, action, before/after values | CRITICAL |
| SR-004.2 | Audit logs stored in append-only, tamper-evident store | HIGH |
| SR-004.3 | Log entries include correlation ID for request tracing | HIGH |
| SR-004.4 | No passwords, API keys, or PII in log output | CRITICAL |
| SR-004.5 | Log retention: minimum 12 months | MEDIUM |

### SR-005: API Security
| ID | Requirement | Priority |
|---|---|---|
| SR-005.1 | Rate limiting: 100 requests/minute per API key | HIGH |
| SR-005.2 | CORS policy: restrict to known origins | HIGH |
| SR-005.3 | Security headers: X-Content-Type-Options, X-Frame-Options, CSP | HIGH |
| SR-005.4 | API versioning to prevent breaking changes | MEDIUM |
| SR-005.5 | Request/response signing for critical operations (customs, gate) | MEDIUM |

### SR-006: Infrastructure Security
| ID | Requirement | Priority |
|---|---|---|
| SR-006.1 | Database not directly accessible from public network | CRITICAL |
| SR-006.2 | Secrets managed via environment variables or vault (not in code) | CRITICAL |
| SR-006.3 | Docker images scanned for vulnerabilities before deployment | HIGH |
| SR-006.4 | Network segmentation between services | MEDIUM |
| SR-006.5 | Regular dependency updates (security patches) | HIGH |

### SR-007: Container and Cargo Security
| ID | Requirement | Priority |
|---|---|---|
| SR-007.1 | Container seal status tracked and verified at gate operations | HIGH |
| SR-007.2 | Dangerous goods (IMDG) containers flagged and tracked continuously | HIGH |
| SR-007.3 | Container tamper detection alerts (via IoT sensor integration) | MEDIUM |
| SR-007.4 | Customs hold status enforced — system blocks gate-out until release | CRITICAL |

## Role-Based Access Control Matrix

| Action | Admin | Terminal Op | Yard Manager | Gate Operator | Read-Only |
|---|---|---|---|---|---|
| View yard status | YES | YES | YES | YES | YES |
| Modify yard assignments | YES | YES | YES | NO | NO |
| Run optimization | YES | YES | YES | NO | NO |
| Process gate-in | YES | YES | NO | YES | NO |
| Process gate-out | YES | YES | NO | YES | NO |
| Manage appointments | YES | YES | NO | YES | NO |
| View customs data | YES | YES | NO | YES | NO |
| Modify customs status | YES | NO | NO | NO | NO |
| Manage users | YES | NO | NO | NO | NO |
| View audit logs | YES | YES | NO | NO | NO |
| System configuration | YES | NO | NO | NO | NO |

## Security Testing Requirements

| Test Type | Frequency | Responsibility |
|---|---|---|
| Unit test (input validation) | Every commit | Developer |
| Integration test (API security) | Every PR | Developer + QA |
| Penetration testing | Pre-launch | Security Reviewer |
| Dependency vulnerability scan | Weekly | CI/CD pipeline |
| Code review (security focus) | Every PR | Security Reviewer |
| Access control verification | Pre-launch | Security Reviewer |

## Compliance Considerations
- **GDPR:** Container owner data, truck driver PII — data minimization, right to deletion
- **SOLAS (Safety of Life at Sea):** Container weight verification (VGM), security declarations
- **ISPS Code:** Port facility security — access control, monitoring
- **Seveso Directive:** Hazardous materials location tracking (EU terminals)

---
*Security requirements documented: 2026-08-26*
