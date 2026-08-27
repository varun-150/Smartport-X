# NON-FUNCTIONAL REQUIREMENTS — SmartPortX

## Performance Requirements

### PRF-001: Response Time
| Scenario | Target | Measurement |
|---|---|---|
| Yard map render (10,000 containers) | < 3 seconds | Time from request to full render |
| Container search (by ID) | < 1 second | Time from query to result |
| Container search (by criteria) | < 2 seconds | Time from query to result list |
| Dashboard refresh | < 5 seconds | Time between data updates |
| Stacking optimization (per batch) | < 30 seconds | Time to compute recommendation |
| Gate transaction processing | < 30 seconds | End-to-end gate-in or gate-out |
| API endpoint response (standard) | < 500ms | P95 latency |
| WebSocket message delivery | < 1 second | From event to subscriber |

### PRF-002: Throughput
| Metric | Target |
|---|---|
| Concurrent users | 50+ |
| Concurrent API requests | 200+ per second |
| Container movements processed per hour | 500+ |
| Gate transactions per hour | 100+ trucks |
| Real-time event processing | 1,000+ events/second |

### PRF-003: Resource Utilization
| Resource | Target |
|---|---|
| Application memory (per service instance) | < 512MB |
| Database connections (pool) | 50 max per service |
| CPU utilization (normal load) | < 60% |
| CPU utilization (peak load) | < 85% |

## Scalability Requirements

### SCL-001: Data Volume
| Metric | Target |
|---|---|
| Containers in yard | 10,000+ |
| Container history records | 1,000,000+ (12 months) |
| Gate transaction records | 500,000+ per year |
| Sensor data points | 10,000+ per hour |
| Concurrent appointments | 200+ per time window |

### SCL-002: Horizontal Scaling
| Component | Scaling Strategy |
|---|---|
| API Gateway | Stateless; scale horizontally |
| Yard Management Service | Stateless; scale horizontally |
| Optimization Service | Compute-intensive; scale with worker nodes |
| Event Processing | Event-driven; partition by topic |
| Database | Read replicas; partition by time/container |

## Availability Requirements

### AVL-001: Uptime
| Period | Target |
|---|---|
| Business hours (06:00-22:00) | 99.5% |
| Off-hours (22:00-06:00) | 99.0% |
| Planned maintenance windows | Monthly, 2-hour max |
| Recovery Time Objective (RTO) | < 30 minutes |
| Recovery Point Objective (RPO) | < 5 minutes |

### AVL-002: Fault Tolerance
| Scenario | Expected Behavior |
|---|---|
| Single service failure | Other services continue; degraded functionality |
| Database failure | Read from replica; write queue until primary recovers |
| Network partition | Graceful degradation; queue events for replay |
| Optimization service down | Core yard operations continue; manual override available |

## Security Requirements

### SEC-001: Authentication and Authorization
| Requirement | Implementation |
|---|---|
| User authentication | JWT-based with refresh tokens |
| API authentication | API key + JWT for service-to-service |
| Role-based access control (RBAC) | 5 roles: Admin, Terminal Operator, Yard Manager, Gate Operator, Read-Only |
| Session management | 30-minute timeout; refresh token rotation |
| Password policy | Min 12 chars; complexity rules; bcrypt hashing |

### SEC-002: Data Protection
| Requirement | Implementation |
|---|---|
| Data in transit | TLS 1.3 for all communications |
| Data at rest | AES-256 for sensitive fields (customs data) |
| API rate limiting | 100 requests/minute per client |
| Input validation | Server-side validation on all inputs |
| SQL injection prevention | Parameterized queries only |
| XSS prevention | Output encoding; CSP headers |

### SEC-003: Audit and Compliance
| Requirement | Implementation |
|---|---|
| Audit logging | All state changes logged with timestamp, user, action |
| Log retention | 12 months |
| Log integrity | Immutable append-only log store |
| Access logging | All API calls logged with client identity |
| Error handling | No sensitive data in error responses |

## Integration Requirements

### INT-001: EDIFACT Message Support
| Message | Standard | Direction |
|---|---|---|
| COPRAR | UN/EDIFACT | Inbound (shipping line → terminal) |
| COARRI | UN/EDIFACT | Outbound (terminal → shipping line) |
| CODECO | UN/EDIFACT | Outbound (terminal → carrier) |
| COPARN | UN/EDIFACT | Inbound (carrier → terminal) |
| CUSCAR | UN/EDIFACT | Inbound (carrier → customs) |
| CUSDEC | UN/EDIFACT | Inbound (broker → customs) |

### INT-002: API Standards
| Standard | Requirement |
|---|---|
| API specification | OpenAPI 3.0 |
| API style | RESTful with JSON |
| Versioning | URL-based (/api/v1/) |
| Pagination | Cursor-based for large result sets |
| Error format | RFC 9457 Problem Details |
| WebSocket | For real-time event subscriptions |

### INT-003: IoT Protocol Support
| Protocol | Use Case |
|---|---|
| MQTT | Container sensor data (location, temperature) |
| HTTP/REST | Equipment telemetry (crane status) |
| OPC-UA | Industrial equipment integration (future) |

## Maintainability Requirements

### MNT-001: Code Quality
| Metric | Target |
|---|---|
| Code documentation (public APIs) | Javadoc/OpenAPI for 100% |
| Unit test coverage (business logic) | 80%+ |
| Integration test coverage | 60%+ |
| Static analysis | Zero critical/blocker issues |
| Code review | All PRs reviewed before merge |

### MNT-002: Deployment
| Aspect | Target |
|---|---|
| Deployment automation | CI/CD pipeline (GitHub Actions) |
| Database migrations | Flyway automated |
| Environment parity | Docker containers for all services |
| Rollback capability | < 5 minutes to previous version |

## Operational Requirements

### OPS-001: Monitoring
| Metric | Tool |
|---|---|
| Application metrics | Micrometer + Prometheus |
| Log aggregation | Structured JSON logs |
| Distributed tracing | OpenTelemetry |
| Health checks | Spring Boot Actuator |
| Alerting | Configurable thresholds |

### OPS-002: Documentation
| Document | Audience |
|---|---|
| API documentation (OpenAPI) | Developers, integrators |
| Architecture documentation | Development team |
| Operations runbook | Operations team |
| User manual | Terminal operators |

---
*Non-functional requirements documented: 2026-08-26*
