# USE CASES — SmartPortX

## UC-001: Yard Manager — Optimize Container Stacking
**Actor:** Yard Manager
**Precondition:** Vessel discharge plan received; truck appointments scheduled
**Trigger:** Container arrives at yard from quayside transport

| Step | Action | System Response |
|---|---|---|
| 1 | Container arrives at assigned yard block | System verifies container ID via RFID/OCR |
| 2 | — | System checks optimization recommendation |
| 3 | — | System suggests optimal stack position (Block/Bay/Row/Tier) |
| 4 | Yard crane operator receives assignment | System assigns crane task with position |
| 5 | Container placed at recommended position | System updates yard map in real-time |
| 6 | — | System recalculates upcoming retrieval optimization |

**Alternative Flows:**
- 3a: If recommended position occupied → system suggests nearest available slot
- 3b: If dangerous goods → system enforces segregation rules
- 5a: If crane unavailable → system queues task and notifies manager

**Success Criteria:** Container placed at optimized position; reshuffling probability minimized

---

## UC-002: Gate Operator — Process Inbound Truck
**Actor:** Gate Operator (or automated gate system)
**Precondition:** Truck has valid appointment; container cleared by customs
**Trigger:** Truck arrives at terminal gate

| Step | Action | System Response |
|---|---|---|
| 1 | Truck approaches gate | System initiates OCR scan of container number |
| 2 | Driver presents Delivery Order + ID | System verifies appointment in TAS |
| 3 | — | System checks customs clearance status |
| 4 | — | System assigns yard location for pickup/drop-off |
| 5 | Gate barrier opens | System logs gate-in event (CODECO message) |
| 6 | Truck proceeds to yard block | System notifies yard crane of incoming truck |
| 7 | Container loaded/unloaded | System confirms completion |
| 8 | Truck exits through gate | System logs gate-out, issues EIR |

**Alternative Flows:**
- 2a: No valid appointment → system offers next available slot or turns away
- 3a: Customs not cleared → system blocks entry, notifies customs broker
- 5a: Container not found at expected location → system triggers search protocol

**Success Criteria:** Truck processed in < 5 minutes; container located and handled

---

## UC-003: Trucking Company — Book Appointment
**Actor:** Trucking Company Dispatcher
**Precondition:** Delivery order received from freight forwarder
**Trigger:** Need to schedule container pickup/delivery

| Step | Action | System Response |
|---|---|---|
| 1 | Dispatcher opens TAS portal | System displays available time slots |
| 2 | Dispatcher selects preferred slot | System checks capacity for that slot |
| 3 | Dispatcher provides truck ID, container number | System validates container exists in system |
| 4 | Dispatcher submits booking | System confirms or suggests alternatives |
| 5 | Dispatcher receives confirmation | System adds to yard crane schedule |

**Alternative Flows:**
- 2a: Slot full → system shows next available slots
- 3a: Container not found → system rejects booking with reason
- 4a: Terminal equipment down → system reduces slot capacity, may reject

**Success Criteria:** Appointment confirmed with specific time window and yard location

---

## UC-004: Terminal Operator — Monitor Yard Performance
**Actor:** Terminal Operator
**Precondition:** System running with live data
**Trigger:** Regular operational review or exception alert

| Step | Action | System Response |
|---|---|---|
| 1 | Operator opens dashboard | System displays real-time yard overview |
| 2 | Operator reviews utilization metrics | System shows block-by-block utilization |
| 3 | Operator checks reshuffling rate | System displays reshuffling statistics |
| 4 | Operator reviews truck turnaround times | System shows gate wait time trends |
| 5 | Operator identifies bottleneck block | System suggests rebalancing actions |
| 6 | Operator approves or adjusts plan | System implements changes |

**Success Criteria:** Operator has full visibility; can identify and resolve issues proactively

---

## UC-005: Customs Authority — Verify Container Status
**Actor:** Customs Officer
**Precondition:** Container declared for import/export
**Trigger:** Customs inspection request or routine check

| Step | Action | System Response |
|---|---|---|
| 1 | Customs queries container in system | System displays container details, location, status |
| 2 | Customs places hold on container | System blocks gate-out for that container |
| 3 | Inspection scheduled | System assigns inspection location |
| 4 | Inspection completed | System updates clearance status |
| 5 | Release authorized | System enables gate-out |

**Success Criteria:** Customs can track, hold, and release containers with full audit trail

---

## UC-006: Yard Manager — Handle Equipment Failure
**Actor:** Yard Manager
**Precondition:** Equipment failure detected during operations
**Trigger:** Crane breakdown or malfunction alert

| Step | Action | System Response |
|---|---|---|
| 1 | Crane operator reports failure | System marks crane as unavailable |
| 2 | — | System identifies affected yard block(s) |
| 3 | — | System recalculates crane assignments for remaining cranes |
| 4 | — | System checks if gate appointments need adjustment |
| 5 | Manager reviews revised plan | System displays impact analysis |
| 6 | Manager approves alternatives | System notifies affected trucking companies |

**Alternative Flows:**
- 4a: If insufficient cranes → system suggests reducing gate appointments
- 5a: If vessel affected → system escalates to vessel planner

**Success Criteria:** Operations continue with minimal disruption; affected parties notified

---

## UC-007: Freight Forwarder — Track Container End-to-End
**Actor:** Freight Forwarder
**Precondition:** Container in transit (export or import)
**Trigger:** Customer inquiry or routine tracking

| Step | Action | System Response |
|---|---|---|
| 1 | Forwarder queries container ID | System displays current location and status |
| 2 | — | System shows: vessel name, yard position, customs status, gate status |
| 3 | — | System shows estimated availability for pickup/delivery |
| 4 | Forwarder shares status with customer | — |

**Success Criteria:** Single query provides complete container status across all domains

---

## UC-008: System — Rebalance Yard Under Disruption
**Actor:** System (automated)
**Precondition:** Operational disruption detected (equipment failure, schedule change)
**Trigger:** Vessel delay, equipment breakdown, or truck surge

| Step | Action | System Response |
|---|---|---|
| 1 | System detects deviation from plan | Triggers replanning workflow |
| 2 | — | Recalculates yard assignments for affected containers |
| 3 | — | Adjusts crane scheduling |
| 4 | — | Updates gate appointment quotas if needed |
| 5 | — | Sends notifications to affected stakeholders |
| 6 | — | Logs all changes for audit trail |

**Success Criteria:** System adapts to disruption automatically; human notified for approval

---

## Use Case Priority

| Use Case | Priority | Complexity | Student Feasible |
|---|---|---|---|
| UC-001: Optimize Stacking | CRITICAL | High | YES (with algorithm) |
| UC-002: Process Truck | CRITICAL | Medium | YES |
| UC-003: Book Appointment | CRITICAL | Low-Medium | YES |
| UC-004: Monitor Performance | HIGH | Medium | YES |
| UC-005: Verify Container | HIGH | Low | YES |
| UC-006: Handle Failure | HIGH | Medium | YES |
| UC-007: Track Container | HIGH | Low | YES |
| UC-008: Rebalance Yard | MEDIUM | High | YES (simplified) |

---
*Use cases documented: 2026-08-26*
