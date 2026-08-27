# SCOPE — SmartPortX

## In Scope (SmartPortX Will Implement)

### Core Platform (MUST HAVE)
| Area | Included | Boundaries |
|---|---|---|
| **Yard Management Service** | Container tracking, yard layout, CRUD operations | Single terminal scope; no multi-terminal |
| **Stacking Optimization Engine** | AI-driven placement recommendations | Algorithm runs synchronously; no real-time streaming |
| **Gate Management Service** | Appointment booking, gate processing workflow | Single-gate model; no multi-gate traffic routing |
| **Container Tracking Service** | Location tracking, movement history | Simulated IoT data; physical sensors are out of scope |
| **Event Processing** | Domain events for container moves, gate events | Event log; no complex event processing (CEP) |
| **REST API** | OpenAPI 3.0 specification; all CRUD + business operations | Synchronous API; no async processing for MVP |
| **Web Dashboard** | Role-based views for yard, gate, vessel status | Single-page application; no mobile app |
| **EDIFACT Message Parsing** | COPRAR, COARRI, CODECO, COPARN parsers | Inbound parsing; no outbound EDIFACT generation |
| **Multi-Stakeholder Views** | Different data views per role | Read-only views; no separate portals |

### Integration Layer (SHOULD HAVE)
| Area | Included | Boundaries |
|---|---|---|
| **IoT Data Ingestion** | MQTT endpoint for simulated sensor data | No physical sensor integration |
| **Customs Workflow** | Status tracking, hold/release workflow | No real customs system integration |
| **WebSocket Events** | Real-time updates to dashboard | Simple pub/sub; no complex filtering |
| **Data Import** | CSV/bulk data import for historical data | No automated data migration tools |

### Intelligence Features (SHOULD HAVE)
| Area | Included | Boundaries |
|---|---|---|
| **Historical Analytics** | Dwell time, throughput, reshuffling metrics | Aggregated reports; no drill-down |
| **Equipment Status Tracking** | Manual status updates for cranes | No sensor-based predictive maintenance |
| **Workload Balancing** | Suggest crane allocation across blocks | No real-time crane scheduling |

## Out of Scope (SmartPortX Will NOT Implement)

| Area | Exclusion Reason |
|---|---|
| **Full TOS replacement** | Enterprise scope; SmartPortX complements, not replaces |
| **Vessel planning (stowage optimization)** | NP-hard standalone problem; requires deep maritime expertise |
| **Quay crane operations** | Physical automation out of scope; focus on yard |
| **AGV/ASC control systems** | Industrial automation; requires hardware |
| **Physical IoT sensor deployment** | Hardware procurement/installation beyond project scope |
| **Real customs system integration** | Regulatory compliance varies by country |
| **Multi-terminal orchestration** | Single terminal scope for MVP |
| **Mobile applications** | Web-first; mobile as future enhancement |
| **AI/ML model training pipeline** | Use pre-built algorithms; no custom ML training |
| **Full PCS functionality** | PCS is country-specific; we provide PCS-like data exchange |
| **Financial systems (invoicing, billing)** | Domain boundary; other systems handle this |
| **HR and workforce management** | Not core port logistics domain |
| **Physical security (CCTV, access gates)** | Infrastructure concern; not software scope |

## Scope Boundaries

### What Defines "Done" for SmartPortX MVP
1. Yard management service operational with 10,000+ container capacity
2. Stacking optimization providing recommendations (not mandatory execution)
3. Gate appointment system functional with dynamic quotas
4. Container tracking with simulated IoT data
5. Dashboard showing real-time yard, gate, and vessel status
6. EDIFACT parsing for key messages (COPRAR, COARRI, CODECO, COPARN)
7. Role-based access control with 5 user roles
8. REST API documented with OpenAPI 3.0
9. All integration tests passing
10. Security requirements met (SR-001 through SR-007)

### Minimum Viable Product (MVP) Scope
The MVP focuses on demonstrating core value:
- Container can be tracked from yard entry to gate-out
- Yard manager can see optimization recommendations
- Trucking company can book appointments
- Terminal operator has real-time visibility
- System handles basic disruption scenarios

### Future Enhancements (Not MVP)
| Enhancement | Depends On |
|---|---|
| Mobile app for truck drivers | Web dashboard stable |
| Real customs system integration | Country-specific regulatory work |
| Multi-terminal support | Single terminal proven |
| Advanced ML models (reinforcement learning) | Algorithm baseline established |
| Real IoT hardware integration | Simulation validated |
| Full PCS certification | Standards compliance verified |

## Assumptions

| ID | Assumption | Impact if Wrong |
|---|---|---|
| A-001 | Technology stack will be Java/Spring Boot backend | Architecture redesign |
| A-002 | PostgreSQL sufficient for data storage | Database migration needed |
| A-003 | Single-terminal scope is acceptable | Significant scope increase |
| A-004 | Simulated IoT data acceptable for MVP | Hardware procurement needed |
| A-005 | EDIFACT parsing libraries available in Java | Custom parser development |
| A-006 | 3-4 team members available for implementation | Timeline extension |
| A-007 | 4-6 week implementation timeline | Extended timeline |

## Constraints

| Constraint | Impact |
|---|---|
| Student project timeline (semester) | Limits feature count |
| No physical port access for testing | Must use simulated/realistic data |
| No commercial TOS license | Must build from scratch |
| Single development machine initially | Limits parallel development |
| No production deployment required | Demo/proof-of-concept quality sufficient |

---
*Scope defined: 2026-08-26*
