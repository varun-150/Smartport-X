# REQUIREMENT TRACEABILITY — SmartPortX

## Traceability Matrix

Links each requirement to its source problem, workflow, stakeholder, and verification method.

### Functional Requirements Traceability

| Req ID | Problem | Workflow | Stakeholder | Verification |
|---|---|---|---|---|
| FR-001.1 | P-003, P-005 | WF-4 | Yard Manager | Unit test: yard layout render |
| FR-001.2 | P-003 | WF-1,2,3,4 | Yard Manager | Integration test: container CRUD |
| FR-001.3 | P-001 | WF-4 | Yard Manager | Unit test: assignment algorithm |
| FR-001.4 | P-005 | WF-4 | Yard Manager | Manual test: override workflow |
| FR-001.5 | P-006 | WF-1 | Terminal Operator | Unit test: container type handling |
| FR-001.6 | P-006 | WF-1,2 | Yard Manager | Unit test: IMDG segregation rules |
| FR-001.7 | P-005 | WF-4 | Terminal Operator | Integration test: metrics computation |
| FR-001.8 | P-003 | WF-2,3 | Gate Operator | Integration test: search queries |
| FR-002.1 | P-001 | WF-4 | Yard Manager | Algorithm test: stacking quality |
| FR-002.2 | P-002 | WF-4 | Yard Manager | Integration test: appointment data flow |
| FR-002.3 | P-001 | WF-4 | Yard Manager | Algorithm test: reshuffling prediction |
| FR-002.4 | P-001 | WF-4 | Terminal Operator | Metric validation: reshuffling rate |
| FR-002.5 | P-005 | WF-4 | Yard Manager | Integration test: replanning trigger |
| FR-002.6 | WF-1 | WF-1 | Yard Manager | Unit test: weight-based rules |
| FR-003.1 | P-002 | WF-2,3 | Trucking Company | Integration test: booking flow |
| FR-003.2 | P-002 | WF-2,3 | Yard Manager | Unit test: quota calculation |
| FR-003.3 | P-002 | WF-2,3 | Yard Manager | Integration test: dynamic adjustment |
| FR-003.4 | P-002 | WF-2 | Gate Operator | Integration test: gate-in flow |
| FR-003.5 | P-002 | WF-2 | Gate Operator | Integration test: gate-out flow |
| FR-003.6 | P-002 | WF-2 | Gate Operator | Dashboard test: queue display |
| FR-003.7 | P-002 | WF-2,3 | Trucking Company | Integration test: dual transaction |
| FR-003.8 | P-002 | WF-2,3 | Trucking Company | Integration test: notification delivery |
| FR-004.1 | P-004 | WF-1 | Shipping Line | Integration test: manifest import |
| FR-004.2 | P-001 | WF-1 | Yard Manager | Integration test: discharge plan |
| FR-004.3 | WF-1 | WF-1 | Terminal Operator | Dashboard test: progress display |
| FR-004.4 | P-004 | WF-1 | Shipping Line | Unit test: COPRAR parsing |
| FR-004.5 | P-004 | WF-1 | Shipping Line | Unit test: COARRI parsing |
| FR-005.1 | P-004 | WF-6 | Customs | Integration test: status tracking |
| FR-005.2 | P-006 | WF-6 | Customs | Integration test: gate-out block |
| FR-005.3 | P-004 | WF-6 | Customs | Unit test: CUSCAR parsing |
| FR-005.4 | P-004 | WF-2 | Gate Operator | Unit test: CODECO parsing |
| FR-005.5 | P-006 | WF-6 | Customs | Integration test: audit trail |
| FR-006.1 | P-003, P-004 | All | All | UI test: role-based views |
| FR-006.2 | P-003 | WF-4 | Terminal Operator | Dashboard test: yard status |
| FR-006.3 | P-002 | WF-2 | Gate Operator | Dashboard test: gate status |
| FR-006.4 | WF-1 | WF-1 | Terminal Operator | Dashboard test: vessel status |
| FR-006.5 | P-001 | WF-4 | Terminal Operator | Integration test: historical data |
| FR-007.1 | P-003 | All | Yard Manager | Integration test: IoT ingestion |
| FR-007.2 | P-003 | All | Yard Manager | Integration test: discrepancy detection |
| FR-007.3 | P-003 | All | Yard Manager | Integration test: real-time update |
| FR-007.4 | N/A | N/A | Developer | Unit test: simulated data |
| FR-007.5 | P-003 | All | Freight Forwarder | Integration test: movement history |
| FR-008.1 | P-007 | WF-4 | Yard Manager | Integration test: equipment status |
| FR-008.2 | P-005 | WF-4 | Yard Manager | Unit test: crane scheduling |
| FR-008.3 | P-007 | WF-4 | Yard Manager | Integration test: downtime handling |
| FR-008.4 | P-007 | WF-4 | Terminal Operator | Metric test: moves per crane |

### Non-Functional Requirements Traceability

| Req ID | Category | Verification Method |
|---|---|---|
| NFR-001.1-001.8 | Performance | Load testing with JMeter/Gatling |
| NFR-002.1-002.3 | Scalability | Data volume testing |
| NFR-003.1-003.2 | Availability | Failover testing |
| NFR-004.1-004.4 | Security | Penetration testing, code review |
| NFR-005.1-005.4 | Integration | EDIFACT parsing tests, API compliance |
| NFR-006.1-006.3 | Maintainability | Code coverage reports, documentation review |

### Security Requirements Traceability

| Req ID | Threat Addressed | Verification |
|---|---|---|
| SR-001.1-001.7 | Spoofing, Elevation of Privilege | Auth integration tests |
| SR-002.1-002.5 | Information Disclosure | TLS config verification, data filtering tests |
| SR-003.1-003.5 | Tampering, Injection | Input validation tests, SQL injection tests |
| SR-004.1-004.5 | Repudiation | Audit log verification |
| SR-005.1-005.5 | DoS, Tampering | Rate limiting tests, security header checks |
| SR-006.1-006.5 | All categories | Infrastructure security review |
| SR-007.1-007.4 | Container security | Customs workflow tests |

## Gap Analysis

| Problem | Covered by Requirements? | Gap? |
|---|---|---|
| P-001: Reshuffling | FR-002.1, FR-002.3, FR-002.4 | NONE |
| P-002: Gate Congestion | FR-003.1-003.8 | NONE |
| P-003: Container Visibility | FR-001.2, FR-007.1-007.5 | NONE |
| P-004: Data Silos | FR-004.1-004.5, FR-005.1-005.5 | NONE |
| P-005: Yard Optimization | FR-002.1-002.6, FR-008.1-008.4 | NONE |
| P-006: Dangerous Goods | FR-001.6, FR-005.2, FR-007.1 | NONE |
| P-007: Equipment Failure | FR-008.1-008.4, NFR-003.2 | NONE |
| P-008: Export Cut-Off | FR-003.1-003.3 (appointment) | PARTIAL — no explicit cut-off notification |

**Finding:** One gap identified — explicit export cut-off notification not covered. Recommend adding FR-003.9 (system shall notify exporters of approaching cut-off deadlines) in next iteration.

---
*Traceability matrix completed: 2026-08-26*
