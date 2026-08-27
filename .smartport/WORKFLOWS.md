# WORKFLOWS — SmartPortX

## Workflow 1: Vessel Arrival and Container Discharge

```
Shipping Line                    Port Authority              Terminal Operator              Yard Manager
    |                                  |                           |                           |
    |-- ETA + Manifest (72h) -------->|                           |                           |
    |                                 |-- Berth Allocation ------>|                           |
    |                                 |                           |-- Create discharge plan -->|
    |                                 |                           |                           |
    |<-- Berth Confirmed ------------|                           |                           |
    |                                 |                           |                           |
    |-- Stowage Plan (COPRAR) ------->|-------------------------->|                           |
    |                                 |                           |-- Assign yard locations -->|
    |                                 |                           |                           |
    |== Vessel Arrives, Berths ======|===========================|===========================|
    |                                 |                           |                           |
    |                                 |                           |-- STS Crane Discharge --->|
    |                                 |                           |   (CMPH target: 25-35)   |
    |                                 |                           |                           |
    |                                 |                           |--> Transport to Yard ----->|
    |                                 |                           |   (AGV / Terminal Tractor) |
    |                                 |                           |                           |
    |                                 |                           |                           |-- RTG/ASC Stack
    |                                 |                           |                           |   at assigned location
    |                                 |                           |                           |
    |-- Discharge Confirmation <-----|<---------------------------|<--------------------------|
    |                                 |                           |                           |
```

**Edge Cases:**
- Vessel arrives early (berth not ready)
- Vessel arrives late (other vessels waiting)
- Wrong container position on vessel vs. stowage plan
- Dangerous goods (IMDG) requiring special handling
- Reefer containers requiring immediate power connection
- Equipment failure mid-discharge (STS crane breakdown)

## Workflow 2: Import Container Pickup (Truck to Gate-Out)

```
Freight Forwarder            Customs              Terminal Operator           Truck Driver
    |                           |                        |                         |
    |-- File Declaration ------>|                        |                         |
    |                           |                        |                         |
    |<-- Customs Clearance -----|                        |                         |
    |                           |                        |                         |
    |-- Request Delivery Order ------------------------->|                         |
    |                           |                        |-- Allocate yard slot -->|
    |                           |                        |                         |
    |<-- Delivery Order Issued -------------------------|                         |
    |                           |                        |                         |
    |                           |                        |-- Book appointment ---->|  (via TAS)
    |                           |                        |                         |
    |                           |                        |<-- Confirm Appointment  |
    |                           |                        |                         |
    |                           |                        |                         |-- Arrive at Gate
    |                           |                        |                         |   (with Delivery Order + ID)
    |                           |                        |                         |
    |                           |                        |-- Gate Processing ----->|
    |                           |                        |   (OCR, doc check,      |
    |                           |                        |    container inspection)|
    |                           |                        |                         |
    |                           |                        |-- Send to Yard Block -->|
    |                           |                        |                         |
    |                           |                        |                         |-- RTG retrieves container
    |                           |                        |                         |   (potential reshuffling!)
    |                           |                        |                         |
    |                           |                        |<-- Container Loaded ----|
    |                           |                        |                         |
    |                           |                        |-- Exit Gate Process --->|
    |                           |                        |   (EIR, seal check)     |
    |                           |                        |                         |
    |                           |                        |== Truck Exits Terminal =|
```

**Key Metrics:**
- Truck Turnaround Time (TTT): target < 45 minutes
- Gate processing time: target < 5 minutes per truck
- Average wait time at yard block: varies (0-30+ minutes)
- Reshuffling rate: target < 15% of moves

## Workflow 3: Export Container Delivery (Truck to Yard)

```
Exporter/Forwarder           Terminal Operator           Truck Driver
    |                             |                          |
    |-- Book space with Line ---->|                          |
    |                             |                          |
    |<-- Entry Permit Issued -----|                          |
    |                             |                          |
    |== Container arrives at gate (with export docs) =======|
    |                             |                          |
    |                             |-- Gate Processing ------>|
    |                             |   (OCR, doc check,       |
    |                             |    customs release,      |
    |                             |    weight verification)  |
    |                             |                          |
    |                             |-- Assign yard location ->|
    |                             |                          |
    |                             |-- RTG loads container -->|  (into outbound stack)
    |                             |                          |
    |                             |-- Exit Gate ----------->|
    |                             |                          |
```

**Critical Rule:** Export container must arrive before vessel cut-off time (typically 24-72 hours before vessel arrival).

## Workflow 4: Yard Management and Optimization

```
Yard Manager (via TOS)
    |
    |-- Receive vessel discharge plan + truck appointment schedule
    |
    |-- STEP 1: Yard Block Allocation
    |   (Assign containers to blocks based on destination, vessel, retrieval order)
    |
    |-- STEP 2: Container Task Allocation
    |   (Distribute tasks per block per time period to balance workload)
    |
    |-- STEP 3: Yard Crane Deployment
    |   (Assign RTGs/ASCs to blocks based on task load)
    |
    |-- Ongoing: Dynamic Rebalancing
    |   (Adjust assignments based on actual arrivals, delays, equipment status)
    |
    |-- Output: Yard Plan + Crane Schedule
```

**Bidirectional Demand Response Mode (Advanced):**
- Proactively integrates both vessel operation plans AND truck arrival demands
- Yard plans dynamically respond to both seaside and landside demands
- Requires joint optimization of all three sub-problems under unified information set
- This mode is the current research frontier — most existing systems only do unidirectional

## Workflow 5: Gate Appointment System (TAS)

```
Terminal Operator                    Trucking Company
    |                                      |
    |-- Set appointment quotas --------->|
    |   (per time window, based on        |
    |    terminal workload forecast)      |
    |                                      |
    |-- Upload to TAS portal ---------->|
    |                                      |
    |                                   |-- Browse available slots
    |                                   |-- Select preferred slot
    |                                   |-- Provide truck + container info
    |                                      |
    |<-- Booking Request ----------------|
    |                                      |
    |-- Validate: slot available? ------>|
    |   capacity exceeded?              |
    |   container cleared?              |
    |                                      |
    |-- Confirm / Reject --------------->|
    |                                      |
    |                                   |-- If confirmed: arrive at assigned time
    |                                   |-- If rejected: select alternate slot
    |                                      |
```

**Dynamic Adjustments:**
- Terminal can adjust quotas in real-time based on vessel schedule changes
- Equipment breakdown triggers automatic quota reduction
- Peak-hour pricing may incentivize off-peak arrivals

## Workflow 6: Customs Clearance Chain

```
Customs Broker                 Customs Authority           Terminal Operator
    |                              |                           |
    |-- Submit Declaration ------>|                           |
    |   (Bill of Lading,           |                           |
    |    Delivery Order,           |                           |
    |    Cargo Manifest)           |                           |
    |                              |                           |
    |                          |-- Risk Assessment -->|        |
    |                              |                           |
    |                              |-- Release / Hold / -->|   |
    |                              |   Inspection             |
    |                              |                           |
    |<-- Clearance Status ---------|                           |
    |                              |                           |
    |-- Forward to Terminal ---------------------------------->|
    |                              |                           |
    |                              |                           |-- Gate system checks clearance
    |                              |                           |   before allowing pickup
```

## Cross-Cutting Concerns (Apply to All Workflows)

### Data Exchange Standards
- **EDIFACT:** COPRAR (pre-arrival), COARRI (discharge/load confirmation), CODECO (gate-in/out), COPARN (acceptance/release notice)
- **REST APIs:** Modern platforms, PCS integrations
- **Real-time:** IoT sensor data, equipment telemetry

### Failure Modes Across Workflows
| Failure | Workflow(s) Affected | Impact |
|---|---|---|
| TOS system downtime | All | Operations halt or degrade to manual |
| GPS/RFID signal loss | 1, 2, 3, 4 | Container tracking lost |
| Equipment breakdown (crane) | 1, 2, 3, 4 | Throughput reduction, delays |
| Customs system downtime | 2, 6 | Trucks cannot be processed |
| Network failure | All | Data exchange stops |
| Wrong container location | 2, 4 | Extended retrieval time, reshuffling |
| Dangerous goods mishandling | 1, 2, 3 | Safety incident, regulatory violation |

---
*Workflows documented: 2026-08-26*
*Sources: DCSA Blueprint, PSA operations, YILPORT SOPs, peer-reviewed research*
