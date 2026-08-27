# DOMAIN RESEARCH — SmartPortX

## Research Scope
Container port logistics and container yard orchestration — end-to-end operations from vessel arrival through gate-out delivery.

## Primary Sources
- PSA Genova Terminal Operations (field case study, 2025)
- Container Terminal Operations and Technology Report (Avlino, 2025 industry survey)
- DCSA Industry Blueprint 2026.Q1 (container shipping standards body)
- IPCSA Port Community Systems guidelines and message reference
- IMO FAL.5/Circ.42/Rev.4 Port Community System guidelines (2025)
- Multiple peer-reviewed papers on yard management optimization (2024-2026)
- TIC4.0 terminal automation standards initiative

## Domain Overview

### What is a Container Port?
A container port (seaport terminal) is the critical interface between maritime and land-based logistics. Containers arrive by vessel, are temporarily stored in a yard, and then distributed inland via truck, rail, or barge — and vice versa for exports. The terminal functions as a coordination hub where multiple independent stakeholders must operate in tight synchronization under extreme time pressure.

### Core Physical Components
| Component | Function |
|---|---|
| **Quayside** | Berth area where vessels moor; STS (Ship-to-Shore) cranes load/unload containers |
| **Container Yard** | Stacking area organized into blocks, bays, rows, and tiers; temporary buffer between sea and land sides |
| **Gate Complex** | Entry/exit points for external trucks; document verification, container inspection, OCR scanning |
| **Internal Roadways** | Lanes connecting quay, yard blocks, and gate; one-way traffic patterns |
| **Maintenance Area** | Equipment servicing; reefer plug/unplug stations |
| **Control Room** | Central coordination hub; TOS operators monitor all operations |

### Container Yard Layout
A container yard is organized hierarchically:
- **Block** — a large section of the yard (typically 20-30 blocks in a mid-size terminal)
- **Bay** — a position along the length of a block
- **Row** — a position across the width of a block
- **Tier** — a vertical stacking level (typically 4-6 tiers high)

A container's position is identified as Block/Bay/Row/Tier. Containers stack on top of each other, creating dependency chains — retrieving a bottom container requires moving all containers above it (reshuffling/rehandling).

### Container Types
- **20-foot (TEU)** and **40-foot (FEU)** standard dry containers
- **Reefer** containers (refrigerated; require power and temperature monitoring)
- **OOG** (Out-of-Gauge) containers (over-dimensional cargo)
- **IMDG** containers (dangerous goods; require segregation and safety protocols)
- **Empty** containers (require repositioning)

## Operational Workflows (Research Summary)

### 1. Vessel Arrival and Discharge
**Trigger:** Vessel ETA communicated days in advance by shipping line
**Process:**
1. Port Authority allocates berth based on vessel size, draft, tide window, and schedule
2. Vessel arrives, berths, and is secured by marine operations team
3. Shipping line provides stowage plan (bay plan) mapping container positions on vessel
4. STS cranes begin discharge following the discharge sequence
5. Containers move from vessel to internal transport (terminal tractors/AGVs) to yard blocks
6. Yard cranes (RTGs/RMGs/ASCs) stack containers at pre-assigned yard locations

**Key metrics:** Crane Moves Per Hour (CMPH), Vessel Turnaround Time (TAT), Berth Occupancy Rate

### 2. Yard Operations (Storage and Retrieval)
**The yard is where operations either flow or break down.**

**Three interdependent sub-problems:**
1. **Yard crane deployment** — assigning cranes to blocks for container handling
2. **Yard block allocation** — assigning containers to specific blocks
3. **Container task allocation** — assigning specific numbers of tasks per block per period

**Stacking strategies** (research-backed):
- **FIFO (First-In-First-Out):** Lowest reshuffling under high-load conditions; 78% better weight variance reduction with fuzzy clustering
- **Balanced distribution:** Distributes containers evenly across available slots
- **Departure-time clustering:** Groups containers by departure time; improves performance in outbound-dominated scenarios
- **Vertical stacking with weight clustering:** Groups similar-weight containers vertically; reduces reshuffling by 23-78% depending on configuration

**Key problem:** Container reshuffling — unnecessary container movements during retrieval when target container is buried under others. Reshuffling can account for 20-40% of yard crane operations in poorly optimized yards.

### 3. Gate Operations (Truck Processing)
**External truck flow:**
1. Truck arrives at gate with Delivery Order and identification
2. Gate system performs: identity verification, document check, container inspection, OCR scan
3. System assigns yard location and sends truck to designated yard block
4. Yard crane loads/unloads container onto/from truck
5. Truck proceeds to exit gate; EIR (Equipment Interchange Receipt) issued

**Gate Appointment Systems (TAS):**
- Regulate truck arrivals throughout the day to minimize gate congestion
- Terminal operators set appointment quotas per time window
- Trucking companies book preferred time slots
- Dynamic TAS (real-time) outperforms static TAS (pre-scheduled only)
- 4% shift of arrivals from peak hours can reduce idling emissions by one-third

**Dual transactions:** Single truck trip handles both drop-off and pickup, reducing empty runs and gate congestion.

### 4. Documentation and Customs Chain
**Parallel to physical operations:**
1. Shipping line submits manifest 72+ hours before vessel arrival
2. Import manifest processed; customs declarations filed
3. Bill of Lading, Delivery Order, and cargo manifest reviewed by customs
4. Customs clearance status determines when importer can collect container
5. Gate system verifies clearance before allowing pickup

**Key standards:** UN/EDIFACT messages (COPRAR, COARRI, CODECO, COPARN, CUSCAR, CUSDEC), DCSA standards

### 5. Export Flow (Reverse)
1. Exporter books space with shipping line
2. Container arrives at terminal before vessel (cut-off time)
3. Gate processes inbound truck with export container
4. Container stacked in yard in outbound zone
5. When vessel berths, yard cranes retrieve containers and load via STS cranes

## Key Industry Pain Points (Evidence-Based)

### Yard Management Complexity
- Yard management is NP-hard (combines parallel machine scheduling with space constraints and multi-dimensional knapsack problems)
- Inefficient yard management leads to prolonged vessel turnaround times, increased truck waiting times, and systemic throughput bottlenecks
- Most existing research addresses only partial integration of the three sub-problems

### Container Tracking Failures
- 10-60% of containers may be in incorrect location at any given time (Van Moer Logistics ADR terminal)
- Manual container tracking causes positioning errors, operational delays, limited visibility
- GPS/GNSS fails in container yards due to limited sky visibility and signal reflection off metal walls
- Single-frequency GPS systems fail in dense port environments; dual-frequency (L1+L5) needed
- RFID alone cannot provide end-to-end tracking; hybrid (RFID+GPS+BLE+NB-IoT) required

### Gate Congestion
- Uncontrolled truck arrivals cause extended queues, air pollution from idling, and blocked terminal operations
- Yard service capacity uncertain because vessel operations plans change dynamically
- Yard cranes always prioritize vessel operations (berth service) over external truck service, creating workload imbalance
- Truck turnaround time is a primary performance indicator — delays here cascade to customer satisfaction

### Data Silos and Integration
- Each stakeholder operates different systems with different data formats
- PCS platforms exist but vary widely in scope, functionality, and interoperability
- TOS systems are often legacy and difficult to integrate with modern platforms
- Data quality is poor across the industry — shipping lines format data differently; terminals provide different levels of detail
- "Reporting only once" principle is a goal but not yet achieved in most ports

### Equipment and Automation Challenges
- Integration with legacy TOS remains limited
- Capital and change-management costs reduce incentives for full rollout
- Standards (TIC4.0) are emerging but adoption is slow
- Cybersecurity concerns increase with automation level
- Workforce adaptability and retraining requirements

## Industry Structure

### Terminal Operating System (TOS) Market
**Leading vendors:**
- **Navis N4** — market leader, enterprise-grade
- **TBA Group** (Qualcomm/TBA TOS) — strong in Europe
- **IDCS/PowerChain** — North American focus
- **RITMS** — integrated yard and gate
- **Konecranes Autostore** — small/mid-sized terminals
- **OOCL proprietary TOS** — used by OOCL terminals

**Market characteristics:**
- Consolidation: fewer vendors, larger systems
- AI integration: optimization modules being added
- Cloud adoption: slow — security concerns dominate; on-premise preferred
- Open APIs: growing demand for third-party integration

### Port Community Systems (PCs)
**Examples:** Portbase (Netherlands), NxtPort (Belgium), and national platforms
**Functions:** Customs declarations, cargo manifests, vessel call data, port logistics coordination
**Standards:** UN/EDIFACT messages, SMDG container messages, REST APIs
**Limitation:** Many operate only locally; cross-border exchange (IPCSA Network of Trusted Networks) is emerging

### Terminal Automation Spectrum
- **Manual terminal:** Human-operated cranes, manual yard planning, paper-based gate
- **Semi-automated:** Automated stacking cranes (ASCs), manual quay cranes
- **Fully automated:** Automated yard + horizontal transport (AGVs)
- **Completely automated:** End-to-end from STS through gate
- As of 2025: ~76 major terminals (8.3%) are fully or partly automated worldwide

## Research Confidence
| Topic | Confidence | Source Quality |
|---|---|---|
| Vessel arrival/discharge process | HIGH | Multiple operational sources, industry reports |
| Yard management optimization | HIGH | Peer-reviewed research (2024-2026) |
| Gate congestion and TAS | HIGH | Peer-reviewed + industry implementation |
| Container tracking technology | MEDIUM-HIGH | Field trials + research papers |
| PCS integration standards | HIGH | IPCSA/IMO official documentation |
| TOS market landscape | MEDIUM | Vendor reports + industry analysis |
| Automation challenges | MEDIUM-HIGH | Academic reviews + industry case studies |

---
*Research completed: 2026-08-26*
*Sources: 11 web research queries, 30+ sources consulted*
