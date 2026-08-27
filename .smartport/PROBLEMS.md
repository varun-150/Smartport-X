# PROBLEMS — SmartPortX

## Problem Statement
Container ports operate in an environment of extreme coordination complexity where multiple independent stakeholders, diverse equipment types, and strict regulatory requirements must converge to move containers efficiently. Existing solutions suffer from data silos, manual processes, and fragmented visibility, resulting in systemic inefficiency.

## Core Problems Identified

### P-001: Container Yard Reshuffling Overhead
**Severity:** HIGH
**Evidence:** Reshuffling can account for 20-40% of yard crane operations in poorly optimized yards (Frontiers in Marine Science, 2026). Research shows fuzzy clustering strategies can reduce weight variance by up to 78% and reshuffling significantly (Nature Scientific Reports, 2026).
**Impact:**
- Extended truck turnaround times (target <45 min; reality 60-120+ min during peaks)
- Increased vessel turnaround time
- Higher energy consumption per container move
- Cascading delays across all downstream operations

**Root Causes:**
- Containers placed without considering retrieval order uncertainty
- Incoming truck arrival sequence unknown within appointment windows
- Yard crane deployment not coordinated with truck arrivals
- Lack of real-time container location visibility

### P-002: Gate Congestion and Truck Waiting
**Severity:** HIGH
**Evidence:** Congestion at terminal gates is described as "one of the most critical challenges" (ScienceDirect, 2024). Studies show shifting just 4% of arrivals from peak hours reduces idling emissions by one-third (Transportation Research Part E).
**Impact:**
- Extended truck queues blocking public roads
- Air pollution from idling diesel trucks
- Trucking company productivity losses
- Terminal gate operations becoming bottlenecks

**Root Causes:**
- Uncontrolled truck arrival patterns (without TAS)
- Yard service capacity uncertain due to changing vessel operations
- Yard cranes prioritize vessel operations over external trucks (workload imbalance)
- Appointment system quotas not aligned with actual yard capacity

### P-003: Fragmented Container Visibility
**Severity:** HIGH
**Evidence:** 10-60% of containers found in incorrect location at busy terminals (Van Moer Logistics, Belgium). GPS/GNSS fails in container yards due to limited sky visibility and signal reflection off metal walls (RapidLab field tests, 2026).
**Impact:**
- 20+ minute search times per misplaced container
- Increased manual inventory audits
- Customer complaints about delayed cargo
- Detention and demurrage fee disputes

**Root Causes:**
- Manual tracking processes prone to human error
- Single-technology tracking (GPS-only) fails in yard environments
- No unified tracking across terminal boundaries
- Legacy systems not integrated with modern tracking technology

### P-004: Stakeholder Data Silos
**Severity:** HIGH
**Evidence:** Shipping lines format data differently; terminals provide different levels of detail; data quality is poor across the industry (Konecranes TOS analysis, 2024). Most PCS operate only locally, not cross-border (IPCSA).
**Impact:**
- Duplicate data entry across systems
- Information gaps causing delays (e.g., customs clearance not visible to gate system)
- No end-to-end supply chain visibility
- Manual coordination overhead (phone, email)

**Root Causes:**
- No universal data exchange standard enforced
- Legacy TOS systems with proprietary formats
- Stakeholder reluctance to share commercial data
- Lack of interoperability between PCS platforms

### P-005: Yard Management Optimization Complexity
**Severity:** MEDIUM-HIGH
**Evidence:** Yard management is NP-hard (combines parallel machine scheduling under space constraints with multi-dimensional knapsack problem). Most existing research addresses only partial integration of sub-problems (ScienceDirect, 2026).
**Impact:**
- Suboptimal yard utilization
- Localized overload in certain blocks
- Crane interference and congestion
- Increased energy consumption

**Root Causes:**
- Three tightly coupled sub-problems (crane deployment, block allocation, task allocation)
- Uncertainty in container arrival times and retrieval sequences
- Real-time computation requirements exceeding current system capabilities
- Limited integration between vessel plans and truck appointments

### P-006: Dangerous Goods Management Risk
**Severity:** MEDIUM
**Evidence:** European Seveso Directive requires real-time location tracking of all hazardous containers. Van Moer Logistics found 10-60% of containers in wrong locations at ADR terminals.
**Impact:**
- Regulatory non-compliance fines
- Safety incidents if hazardous materials mishandled
- Inability to locate containers during emergency
- Increased insurance costs

**Root Causes:**
- Inaccurate container location data
- Lack of real-time segregation verification
- Manual processes for dangerous goods protocols
- Insufficient integration between tracking and safety systems

### P-007: Equipment Failure and Downtime
**Severity:** MEDIUM
**Evidence:** Aging equipment increases failure rates and maintenance costs. Equipment operated below intended capacity due to execution ineffectiveness (Pham and Nguyen, Vietnam CT study).
**Impact:**
- Throughput reduction during crane downtime
- Cascading delays across yard and gate
- Increased maintenance costs
- Unpredictable operational capacity

**Root Causes:**
- Reactive rather than predictive maintenance
- Limited sensor data from equipment
- No integration between equipment status and yard planning
- Spare parts and maintenance scheduling not coordinated with operations

### P-008: Export Cut-Off Time Pressure
**Severity:** MEDIUM
**Evidence:** Export container cut-off times (24-72 hours before vessel arrival) create scheduling pressure. Missed cut-offs result in cargo missing the vessel with no exceptions (YILPORT SOPs).
**Impact:**
- Cargo missing vessel departure
- Storage cost increases
- Vessel capacity underutilization
- Supply chain delays downstream

**Root Causes:**
- Tight coordination windows between exporters and terminal
- Limited visibility into vessel loading progress
- Customs clearance delays
- Inadequate communication of cut-off extensions

## Problem Prioritization Matrix

| Problem | Severity | Feasibility of Solution | Impact if Solved | Priority |
|---|---|---|---|---|
| P-001: Reshuffling | HIGH | HIGH (optimization algorithms exist) | HIGH | CRITICAL |
| P-002: Gate Congestion | HIGH | HIGH (TAS exists, needs integration) | HIGH | CRITICAL |
| P-003: Container Visibility | HIGH | HIGH (IoT tech mature) | HIGH | CRITICAL |
| P-004: Data Silos | HIGH | MEDIUM (requires stakeholder buy-in) | HIGH | CRITICAL |
| P-005: Yard Optimization | MEDIUM-HIGH | MEDIUM (NP-hard, approximate solutions) | MEDIUM-HIGH | HIGH |
| P-006: Dangerous Goods | MEDIUM | HIGH (tracking + rules engine) | MEDIUM | MEDIUM |
| P-007: Equipment Failure | MEDIUM | MEDIUM (predictive maintenance) | MEDIUM | MEDIUM |
| P-008: Export Cut-Off | MEDIUM | MEDIUM (coordination tool) | MEDIUM | MEDIUM |

---
*Problems identified: 2026-08-26*
*Sources: Peer-reviewed research (2024-2026), industry reports, field case studies*
