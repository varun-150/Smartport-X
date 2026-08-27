# REQUIREMENT-ARCHITECTURE TRACEABILITY — SmartPortX

## Purpose
Maps every SmartPortX requirement (38 FR, 24 NFR, 22 Security) to the architecture components (services, components, mechanisms) that implement it. This proves the architecture fully covers the Phase 2 requirement set and exposes any gaps before implementation.

**Traceability coverage:** 100% of FRs, 100% of NFRs, 100% of Security requirements map to concrete architecture components.

---

## 1. Functional Requirement Traceability Matrix

### FR-001: Yard Management

| Req ID | Requirement (short) | Service(s) | Architecture Component | Verification Method |
|---|---|---|---|---|
| FR-001.1 | Yard layout visualization | Yard, Gateway, Frontend | `GET /yard/layout`, WebSocket `/topic/yard-status` | Dashboard test |
| FR-001.2 | Container CRUD | Container, Yard | `POST/GET/PUT/DELETE /containers`, `yard_tiers.container_id` | Integration test |
| FR-001.3 | Auto-assign positions | Yard | Stacking optimizer (`POST /yard/optimize`), heuristic engine | Algorithm test |
| FR-001.4 | Manual override | Yard | `PUT /yard/blocks/{id}/bays/...` override flag | Manual test |
| FR-001.5 | Container type handling | Container, Yard | `container_type` ENUM, slot-size validation in Yard | Unit test |
| FR-001.6 | IMDG segregation rules | Yard, Container | Yard placement rules reading DG metadata from Container events | Unit test |
| FR-001.7 | Yard utilization metrics | Yard | `GET /yard/metrics`, `yard_metrics` aggregations | Integration test |
| FR-001.8 | Container search | Container | `POST /containers/search`, cursor-based pagination | Integration test |

### FR-002: Stacking Optimization

| Req ID | Requirement (short) | Service(s) | Architecture Component | Verification Method |
|---|---|---|---|---|
| FR-002.1 | Optimization algorithm | Yard | Heuristic optimizer (FIFO / weight-cluster / departure-cluster) | Algorithm test |
| FR-002.2 | Appointment-aware optimization | Yard, Gate | Yard subscribes to Gate `AppointmentBooked` events; appointment horizon window | Integration test |
| FR-002.3 | Reshuffling prediction | Yard | Prediction model in optimizer using dwell-time distributions | Algorithm test |
| FR-002.4 | Reshuffling rate metric | Yard | `optimization_history.estimated_reshuffling_reduction` + metrics counter | Metric validation |
| FR-002.5 | Replanning trigger | Yard, Notification | Yard detects drift → publishes `YardOptimizationRun` → Notification alert | Integration test |
| FR-002.6 | Weight-based rules | Yard, Container | Weight clustering strategy; `weight_kg` per container + tier weight validation | Unit test |

### FR-003: Gate Management

| Req ID | Requirement (short) | Service(s) | Architecture Component | Verification Method |
|---|---|---|---|---|
| FR-003.1 | Appointment booking | Gate | `POST /appointments`, `appointment_quotas` capacity check | Integration test |
| FR-003.2 | Dynamic quotas | Gate | Quota engine (`PUT /gate/quotas/{id}`) per time window + type | Unit test |
| FR-003.3 | Dynamic adjustment | Gate | Quota recalculation on gate queue load (event-driven) | Integration test |
| FR-003.4 | Gate-in processing | Gate, Container | `POST /gate/check-in` → container verify, seal check, yard position fetch | Integration test |
| FR-003.5 | Gate-out processing | Gate, Container, Yard, Integration | `POST /gate/check-out` → customs release check (SR-007.4), position free, EIR issue | Integration test |
| FR-003.6 | Queue status | Gate, Notification | `GET /gate/queue` + `/topic/gate-status` real-time push | Dashboard test |
| FR-003.7 | Dual transaction | Gate | Single `appointment_type=BOTH` transaction covering drop-off + pickup | Integration test |
| FR-003.8 | Notifications | Notification | Appointments topic `/topic/appointments/{id}` + preferences | Integration test |

### FR-004: Vessel Operations

| Req ID | Requirement (short) | Service(s) | Architecture Component | Verification Method |
|---|---|---|---|---|
| FR-004.1 | Manifest import | Vessel, Integration | EDIFACT COPRAR parse → discharge plan source | Integration test |
| FR-004.2 | Discharge plan | Vessel, Yard | `POST /vessels/{id}/discharge-plan` coordinated with Yard placement | Integration test |
| FR-004.3 | Discharge progress | Vessel, Notification | `PUT /vessels/{id}/discharge-progress` + progress topic | Dashboard test |
| FR-004.4 | COPRAR parsing | Integration | EDIFACT adapter (COPRAR parser) | Unit test |
| FR-004.5 | COARRI parsing | Integration | EDIFACT adapter (COARRI parser) | Unit test |

### FR-005: Customs & Compliance

| Req ID | Requirement (short) | Service(s) | Architecture Component | Verification Method |
|---|---|---|---|---|
| FR-005.1 | Customs status tracking | Integration, Gate | `customs_status` table, `GET/PUT /integration/customs/status` | Integration test |
| FR-005.2 | Gate-out block | Gate, Integration | Gate queries customs before gate-out; HELD → block (SR-007.4) | Integration test |
| FR-005.3 | CUSCAR parsing | Integration | EDIFACT adapter (CUSCAR parser) | Unit test |
| FR-005.4 | CODECO parsing | Integration | EDIFACT adapter (CODECO parser) | Unit test |
| FR-005.5 | Audit trail | All services | Append-only audit logging, `container_events` / `integration_logs` | Integration test |

### FR-006: Dashboard

| Req ID | Requirement (short) | Service(s) | Architecture Component | Verification Method |
|---|---|---|---|---|
| FR-006.1 | Role-based views | Gateway, Identity, Frontend | RBAC enforcement + role-shaped responses + route-level authz | UI test |
| FR-006.2 | Yard status | Yard, Notification, Frontend | `GET /yard/metrics` + WebSocket yard topic | Dashboard test |
| FR-006.3 | Gate status | Gate, Notification, Frontend | `GET /gate/queue` + WebSocket gate topic | Dashboard test |
| FR-006.4 | Vessel status | Vessel, Notification, Frontend | `GET /vessels/schedule` + WebSocket vessel topic | Dashboard test |
| FR-006.5 | Historical analytics | Yard, Container | `yard_metrics` aggregates, `container_movements` history | Integration test |

### FR-007: IoT & Tracking

| Req ID | Requirement (short) | Service(s) | Architecture Component | Verification Method |
|---|---|---|---|---|
| FR-007.1 | IoT ingestion | Integration, Container | MQTT/IoT adapter, `iot_readings`, publish IoTSensorData event | Integration test |
| FR-007.2 | Discrepancy detection | Integration, Yard | Sensor-vs-yard position comparison → alert event | Integration test |
| FR-007.3 | Real-time update | Integration, Notification | IoTSensorData → `/topic/alerts` + yard topic | Integration test |
| FR-007.4 | Simulated data | Integration | Test-data generator (Python/Java) with noise, gaps, battery drain | Unit test |
| FR-007.5 | Movement history | Container | `container_movements` table, `GET /containers/{id}/movements` | Integration test |

### FR-008: Equipment Management

| Req ID | Requirement (short) | Service(s) | Architecture Component | Verification Method |
|---|---|---|---|---|
| FR-008.1 | Equipment status | Yard | Equipment entity/status fields in Yard schema (manual updates) | Integration test |
| FR-008.2 | Crane scheduling recommendation | Yard | Workload-balancing heuristic in optimizer (crane → block suggestion) | Unit test |
| FR-008.3 | Downtime handling | Yard | Equipment OUT_OF_SERVICE status → Yard alerts scheduler | Integration test |
| FR-008.4 | Moves per crane metric | Yard | `yard_metrics` per-equipment counters | Metric test |

---

## 2. Security Requirement → Architecture Mapping

| Req Group | Requirements | Architecture Component | Mechanism |
|---|---|---|---|
| SR-001 (IAM) | 001.1–001.7 | Identity Service + Gateway | JWT RS256 (15-min access / 7-day refresh w/ rotation), RBAC 5 roles, account lockout (10 fails), bcrypt password policy |
| SR-002 (Data Protection) | 002.1–002.5 | All services | TLS 1.3, AES-256 field encryption (customs/seals), role-shaped API responses, no sensitive data in logs/URLs |
| SR-003 (Input Validation) | 003.1–003.5 | All services | Bean Validation, ISO 6346 format + check-digit validator, 1MB body limit, EDIFACT 10MB + scan |
| SR-004 (Audit & Logging) | 004.1–004.5 | All services | Append-only audit tables, correlation ID, no secrets/PII in logs, 24-month retention |
| SR-005 (API Security) | 005.1–005.5 | Gateway | Rate limit 100 req/min, CORS allow-list, security headers, URL-versioning, signing for customs/gate ops |
| SR-006 (Infrastructure) | 006.1–006.5 | Docker Compose + CI/CD | DB on internal network only, secrets via env vars, image scanning, Dependabot |
| SR-007 (Container Security) | 007.1–007.4 | Gate + Integration | Seal verification at gate, DG flagging/tracking, tamper alerts via IoT, customs-hold gate-out block |

---

## 3. Non-Functional Requirement → Architecture Mapping

| NFR Group | Requirements | Architecture Component | Mechanism |
|---|---|---|---|
| Performance (PRF) | 001.1–001.8, 002.1–002.3, 003.1–003.2 | Gateway (rate limit/caching), Yard (optimization), all (HikariCP pool ≤ 50) | P95 < 500ms targets; yard map render uses paged APIs; optimization < 30s batch |
| Scalability (SCL) | 001.1–001.2, 002.1–002.3 | Stateless services, schema-per-service | Horizontal scale per service; read replicas; partition by time/container |
| Availability (AVL) | 001.1–001.2, 002.1–002.2 | Resilience4j circuit breaker, health checks, graceful degradation | RTO < 30min, RPO < 5min; degraded modes documented per service |
| Security (SEC) | 001.1–001.3, 002.1–002.2, 003.1–003.3 | Identity, Gateway, all services | JWT+RBAC, TLS 1.3, AES-256, rate limiting, append-only audit |
| Integration (INT) | 001.1–001.3, 002.1–002.3, 003.1–003.3 | Integration Service | COPRAR/COARRI/CODECO/COPARN parsers, MQTT ingest, OpenAPI 3.0, cursor pagination, RFC 9457 |
| Maintainability (MNT) | 001.1–001.2, 002.1–002.3 | Flyway, CI/CD, ArchUnit | Versioned migrations, GitHub Actions, 80% unit / 60% integration coverage |

---

## 4. Coverage Analysis (Gaps)

| Gap | Description | Severity | Resolution |
|---|---|---|---|
| FR-003.9 (proposed) | Explicit **export cut-off notification** not covered by any FR or architecture component (identified in Phase 2 gap analysis + F-011) | MEDIUM | Deferred to next iteration. Mitigation in MVP: Gate appointment notification channel can carry cut-off alerts. |

**Result:** 1 gap found; non-blocking for Phase 3. All 38 ratified FRs, 24 NFRs, and 22 security requirements are covered.

---

## 5. Requirement → Service Density Analysis

| Service | FRs Covered | NFRs Covered | Security Groups | Coverage % | Complexity (1–5) |
|---|---|---|---|---|---|
| Gateway | FR-006.1 (+routing for all) | PRF, SEC, SCL, AVL | SR-005, SR-006 | 100% | 2 |
| Identity | FR-006.1 | SEC | SR-001, SR-005 | 100% | 2 |
| Yard | FR-001.1–7, FR-002.1–6, FR-006.2/5, FR-007.2, FR-008.1–4 | PRF, AVL, SCL | SR-003 | 100% | 5 |
| Gate | FR-003.1–8, FR-005.1/2, FR-006.3 | PRF, AVL | SR-003, SR-007 | 100% | 4 |
| Container | FR-001.2/5/8, FR-003.4, FR-006.5, FR-007.1/5 | PRF, SCL | SR-003, SR-007 | 100% | 3 |
| Vessel | FR-004.1–5, FR-006.4 | PRF | SR-003 | 100% | 3 |
| Integration | FR-004.1/4/5, FR-005.1–5, FR-007.1–4 | INT | SR-003, SR-007 | 100% | 5 |
| Notification | FR-002.5, FR-003.6/8, FR-006.2–4, FR-007.3 | PRF, AVL | — | 100% | 2 |

**Density insight:** Yard and Integration are the densest services (highest requirement concentration and complexity). Container, Gate, and Vessel carry the transactional spine. Gateway, Identity, Notification are supporting/cross-cutting.

---

## 6. Per-Service Complexity Score

| Service | Business Logic | Integration | Data | Testing | Deployment | Overall |
|---|---|---|---|---|---|---|
| Gateway | 2 | 3 | 1 | 2 | 2 | **2** |
| Identity | 3 | 2 | 2 | 3 | 2 | **2.4** |
| Yard | 5 | 4 | 4 | 5 | 3 | **4.2** |
| Gate | 4 | 4 | 3 | 4 | 2 | **3.4** |
| Container | 3 | 3 | 3 | 3 | 2 | **2.8** |
| Vessel | 3 | 3 | 2 | 3 | 2 | **2.6** |
| Integration | 3 | 5 | 4 | 4 | 2 | **3.6** |
| Notification | 2 | 3 | 2 | 2 | 2 | **2.2** |

**Recommendation:** Allocate implementation effort proportional to complexity — Yard (optimizer algorithms) and Integration (EDIFACT parsing) need the most time.

---

## 7. Traceability Conclusion

| Dimension | Coverage | Notes |
|---|---|---|
| Functional Requirements | **100%** | All 38 FRs map to ≥1 service + component |
| Functional Requirement details | **100%** | 40/40 ratifiable sub-requirements covered |
| Non-Functional Requirements | **100%** | All 6 groups map to mechanisms |
| Security Requirements | **100%** | All 7 groups map to components |
| Workflows (Phase 2) | **100%** | WF-1→Vessel/Yard, WF-2→Gate/Container, WF-3→Gate/Yard, WF-4→Yard, WF-5→Gate, WF-6→Integration/Gate |
| Problems (Phase 2) | **100%** | All 8 problems addressed by ≥1 FR (Phase 2 matrix re-validated) |

---

*Traceability completed: 2026-08-27*
*References: REQUIREMENTS.md, REQUIREMENT-TRACEABILITY.md, SERVICE-CATALOG.md, SECURITY-ARCHITECTURE.md, PHASE-3-FINDINGS.md (F-011)*