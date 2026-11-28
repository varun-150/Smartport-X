# REQUIREMENTS — SmartPortX (Phase 2 Final)

## Status: DRAFT — Pending Critic Attack and Multi-Agent Review

---

## Functional Requirements

### FR-001: Container Yard Management
**Priority:** CRITICAL
**Category:** Core Operations
| ID | Requirement | Source | Priority |
|---|---|---|---|
| FR-001.1 | System shall display yard layout organized by blocks, bays, rows, and tiers | Workflow 4 | MUST |
| FR-001.2 | System shall track container position (Block/Bay/Row/Tier) for all containers in yard | P-003 | MUST |
| FR-001.3 | System shall assign incoming containers to yard locations based on optimization rules | P-001 | MUST |
| FR-001.4 | System shall support manual override of automated yard assignments | Workflow 4 | SHOULD |
| FR-001.5 | System shall track container type (20ft, 40ft, reefer, IMDG, empty) | Workflow 1 | MUST |
| FR-001.6 | System shall flag dangerous goods containers requiring special handling | P-006 | MUST |
| FR-001.7 | System shall display yard utilization metrics per block and overall | Stakeholder needs | SHOULD |
| FR-001.8 | System shall support container search by ID, type, status, or location | Workflow 2 | MUST |

### FR-002: AI-Driven Stacking Optimization
**Priority:** CRITICAL
**Category:** Intelligence
| ID | Requirement | Source | Priority |
|---|---|---|---|
| FR-002.1 | System shall calculate optimal stacking positions considering vessel retrieval order | P-001 | MUST |
| FR-002.2 | System shall integrate truck appointment data into stacking decisions (bidirectional) | P-002 | MUST |
| FR-002.3 | System shall predict retrieval sequence and minimize reshuffling probability | P-001 | MUST |
| FR-002.4 | System shall provide reshuffling rate metric (target < 15% of moves) | P-001 | SHOULD |
| FR-002.5 | System shall rebalance yard plan when actual arrivals deviate from forecast | P-005 | SHOULD |
| FR-002.6 | System shall support weight-based stacking for vessel stability | Research papers | SHOULD |

### FR-003: Gate Management and Truck Appointment System
**Priority:** CRITICAL
**Category:** Landside Operations
| ID | Requirement | Source | Priority |
|---|---|---|---|
| FR-003.1 | System shall manage truck appointment booking (time slot, container, truck info) | P-002 | MUST |
| FR-003.2 | System shall calculate appointment quotas per time window based on yard capacity | P-002 | MUST |
| FR-003.3 | System shall dynamically adjust quotas when equipment or vessel schedule changes | P-002 | SHOULD |
| FR-003.4 | System shall process truck gate-in: OCR scan, document verification, container inspection | Workflow 2 | MUST |
| FR-003.5 | System shall process truck gate-out: exit verification, EIR issuance | Workflow 2 | MUST |
| FR-003.6 | System shall display real-time gate queue status and estimated wait times | P-002 | SHOULD |
| FR-003.7 | System shall support dual-transaction appointments (drop-off + pickup in one trip) | Research papers | SHOULD |
| FR-003.8 | System shall notify trucking companies of appointment status changes | P-002 | MUST |

### FR-004: Vessel Operations Integration
**Priority:** HIGH
**Category:** Maritime Operations
| ID | Requirement | Source | Priority |
|---|---|---|---|
| FR-004.1 | System shall receive vessel ETA, manifest, and stowage plan data | Workflow 1 | MUST |
| FR-004.2 | System shall create discharge plan mapping vessel positions to yard locations | Workflow 1 | MUST |
| FR-004.3 | System shall track discharge progress against plan | Workflow 1 | SHOULD |
| FR-004.4 | System shall support EDIFACT COPRAR message parsing for pre-arrival data | PCS standards | SHOULD |
| FR-004.5 | System shall support EDIFACT COARRI message for discharge confirmation | PCS standards | SHOULD |

### FR-005: Customs and Documentation Integration
**Priority:** HIGH
**Category:** Regulatory
| ID | Requirement | Source | Priority |
|---|---|---|---|
| FR-005.1 | System shall track customs clearance status per container | Workflow 6 | MUST |
| FR-005.2 | System shall prevent gate-out of uncleared containers | Workflow 6 | MUST |
| FR-005.3 | System shall support EDIFACT CUSCAR message for customs declarations | PCS standards | SHOULD |
| FR-005.4 | System shall support EDIFACT CODECO message for gate-in/out events | PCS standards | SHOULD |
| FR-005.5 | System shall maintain audit trail of all documentation events | P-006 | MUST |

### FR-006: Multi-Stakeholder Dashboard
**Priority:** HIGH
**Category:** Visibility
| ID | Requirement | Source | Priority |
|---|---|---|---|
| FR-006.1 | System shall provide role-based views (Terminal Operator, Yard Manager, Gate Operator, etc.) | Stakeholder map | MUST |
| FR-006.2 | System shall display real-time yard status (utilization, container density, equipment status) | P-003 | MUST |
| FR-006.3 | System shall display gate status (queue length, wait times, appointment schedule) | P-002 | SHOULD |
| FR-006.4 | System shall display vessel operation status (discharge progress, loading status) | Workflow 1 | SHOULD |
| FR-006.5 | System shall provide historical analytics (dwell time, throughput, reshuffling rate) | P-001 | SHOULD |

### FR-007: Container Tracking Integration
**Priority:** HIGH
**Category:** IoT Integration
| ID | Requirement | Source | Priority |
|---|---|---|---|
| FR-007.1 | System shall accept container location updates from IoT sensors (RFID, GPS) | P-003 | MUST |
| FR-007.2 | System shall detect container location discrepancies (actual vs. expected) | P-003 | SHOULD |
| FR-007.3 | System shall update yard map in real-time as containers move | P-003 | MUST |
| FR-007.4 | System shall support simulated IoT data for development/demo mode | Student project scope | MUST |
| FR-007.5 | System shall provide container movement history (audit trail) | P-003 | SHOULD |

### FR-008: Equipment Management
**Priority:** MEDIUM
**Category:** Operations Support
| ID | Requirement | Source | Priority |
|---|---|---|---|
| FR-008.1 | System shall track yard crane (RTG/ASC) status and assignment | P-007 | SHOULD |
| FR-008.2 | System shall support crane scheduling across blocks | P-005 | SHOULD |
| FR-008.3 | System shall flag equipment downtime and trigger replanning | P-007 | SHOULD |
| FR-008.4 | System shall track container moves per crane (productivity metric) | P-007 | SHOULD |

---

## Non-Functional Requirements

### NFR-001: Performance
| ID | Requirement | Target |
|---|---|---|
| NFR-001.1 | Yard status dashboard refresh rate | < 5 seconds |
| NFR-001.2 | Container search response time | < 2 seconds |
| NFR-001.3 | Gate processing time per truck | < 5 minutes (end-to-end) |
| NFR-001.4 | Stacking optimization computation time | < 30 seconds per batch |
| NFR-001.5 | Concurrent user support | 50+ simultaneous users |

### NFR-002: Scalability
| ID | Requirement | Target |
|---|---|---|
| NFR-002.1 | Container capacity | 10,000+ containers in yard |
| NFR-002.2 | Daily gate transactions | 500+ truck movements |
| NFR-002.3 | Data retention | 12 months operational data |

### NFR-003: Availability
| ID | Requirement | Target |
|---|---|---|
| NFR-003.1 | System uptime during operational hours | 99.5% |
| NFR-003.2 | Graceful degradation when optimization service unavailable | Core operations continue |

### NFR-004: Security
| ID | Requirement | Target |
|---|---|---|
| NFR-004.1 | Role-based access control (RBAC) | All endpoints |
| NFR-004.2 | Data encryption in transit | TLS 1.3 |
| NFR-004.3 | Audit logging of all state changes | Complete trail |
| NFR-004.4 | API authentication | JWT + API key |

### NFR-005: Integration
| ID | Requirement | Target |
|---|---|---|
| NFR-005.1 | EDIFACT message support | COPRAR, COARRI, CODECO, COPARN, CUSCAR |
| NFR-005.2 | REST API | OpenAPI 3.0 specification |
| NFR-005.3 | Real-time updates | WebSocket support |
| NFR-005.4 | IoT protocol support | MQTT for sensor data |

### NFR-006: Maintainability
| ID | Requirement | Target |
|---|---|---|
| NFR-006.1 | Code documentation | Javadoc/OpenAPI for all public APIs |
| NFR-006.2 | Test coverage | 80%+ for business logic |
| NFR-006.3 | Deployment automation | CI/CD pipeline |

---

## Requirements Traceability

| Requirement | Problem Addressed | Workflow | Stakeholder |
|---|---|---|---|
| FR-001 | P-003, P-005 | All | Terminal Operator, Yard Manager |
| FR-002 | P-001, P-005 | Workflow 4 | Yard Manager |
| FR-003 | P-002 | Workflow 2, 3 | Gate Operator, Trucking Co |
| FR-004 | P-004 | Workflow 1 | Shipping Line, Terminal Op |
| FR-005 | P-004, P-006 | Workflow 6 | Customs, Freight Forwarder |
| FR-006 | P-003, P-004 | All | All stakeholders |
| FR-007 | P-003, P-006 | All | Terminal Operator, Yard Manager |
| FR-008 | P-007 | Workflow 4 | Yard Manager |

---
*Requirements documented: 2026-08-26*
*Status: DRAFT — Pending critic attack and multi-agent review*
