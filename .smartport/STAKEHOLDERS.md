# STAKEHOLDERS — SmartPortX

## Stakeholder Map

### Tier 1: Direct Terminal Operations

| Stakeholder | Role | Primary Interest | Key Pain Points |
|---|---|---|---|
| **Terminal Operator** | Runs cranes, yard, gate systems; owns TOS | Maximize throughput, minimize costs | Yard congestion, equipment downtime, data silos |
| **Port Authority** | Governs waterway, allocates berths, sets rules | Revenue, safety, compliance | Vessel congestion, berth utilization, regulatory burden |
| **Yard Manager** | Day-to-day yard operations, equipment allocation | Efficient stacking, crane utilization | Reshuffling, truck wait times, workload imbalance |
| **Gate Operator** | Processes trucks entering/leaving terminal | Fast turnaround, document compliance | Gate congestion, OCR failures, manual processing delays |
| **Crane Operator** | Operates STS cranes, RTGs, ASCs | Safe, efficient moves | Equipment failures, poor visibility, fatigue |

### Tier 2: Maritime Stakeholders

| Stakeholder | Role | Primary Interest | Key Pain Points |
|---|---|---|---|
| **Shipping Line** | Manages vessel schedule, cargo, stowage plan | Fast vessel turnaround, cargo safety | Berth delays, miscommunication, yard inefficiency |
| **Ship Agent** | Manages vessel's port call logistics | Smooth operations for the vessel | Documentation delays, coordination overhead |
| **Stevedore** | Physical loading/unloading, lashing/unlashing | Efficient crane cycles, safety | Equipment availability, weather delays |

### Tier 3: Landside Logistics

| Stakeholder | Role | Primary Interest | Key Pain Points |
|---|---|---|---|
| **Trucking Company** | Transports containers between port and inland | Fast turnaround, predictable scheduling | Gate queues, uncertain wait times, appointment system friction |
| **Truck Driver** | Drives container from port to destination | Quick pickup/delivery, clear instructions | Confusing yard layout, wrong container location, paperwork delays |
| **Freight Forwarder** | Coordinates cargo movement on behalf of shipper | End-to-end visibility, cost control | Data gaps, manual coordination, customs delays |
| **Customs Broker / C&F Agent** | Handles customs declarations and clearance | Fast clearance, compliance | Documentation errors, system downtime, inspection delays |

### Tier 4: Regulatory and Support

| Stakeholder | Role | Primary Interest | Key Pain Points |
|---|---|---|---|
| **Customs Authority** | Controls cargo clearance, security compliance | Trade facilitation + security | Incomplete declarations, risk assessment delays |
| **Port Health/Phytosanitary** | Inspects for biological/hazardous risks | Public safety, compliance | Inspection scheduling, documentation gaps |
| **Empty Container Depot** | Stores and repositions empty containers | Efficient container flow | Visibility into container availability |
| **Warehouse/CFS** | Consolidates/deconsolidates cargo | Timely cargo handling | Disconnect from terminal operations |

### Tier 5: Technology and Infrastructure

| Stakeholder | Role | Primary Interest | Key Pain Points |
|---|---|---|---|
| **TOS Vendor** | Provides terminal operating system | Product adoption, integration | Legacy system constraints, API fragmentation |
| **PCS Operator** | Manages Port Community System | Data exchange facilitation | Stakeholder adoption, interoperability |
| **IoT Sensor Vendor** | Provides tracking hardware (RFID, GPS, etc.) | Reliable data delivery | Signal interference, battery life, cost |
| **Network/Infrastructure Provider** | Provides 5G, WiFi, fiber | Connectivity uptime | Port environment RF challenges |

## Stakeholder Interaction Matrix

| From \ To | Terminal Operator | Shipping Line | Trucking Co | Customs | Freight Forwarder |
|---|---|---|---|---|---|
| **Terminal Operator** | — | Vessel schedule, stowage plans, berth allocation | Gate appointments, container status | Clearance status, cargo declarations | Container status, delivery orders |
| **Shipping Line** | Vessel ETA, manifest, stowage plan | — | Container availability | Customs documentation | Booking confirmations |
| **Trucking Co** | Appointment requests, pickup/drop-off confirmation | Container pickup instructions | — | (via customs broker) | Delivery confirmations |
| **Customs** | Release orders, inspection notices | Import/export declarations | (via customs broker) | — | Clearance certificates |
| **Freight Forwarder** | Container status inquiries | Booking requests | Delivery instructions | Documentation support | — |

## Communication Patterns
- **Formal/Standardized:** UN/EDIFACT messages between systems (COPRAR, COARRI, CODECO, COPARN)
- **API-based:** REST/JSON between modern platforms and PCS
- **Ad-hoc:** Email, phone, radio communications between individuals
- **TOS-centric:** Most operational data flows through or from the TOS

## Decision Authority
| Decision | Primary Authority | Consulted |
|---|---|---|
| Berth allocation | Port Authority | Terminal Operator, Shipping Line |
| Yard block assignment | Yard Manager (via TOS) | — |
| Crane scheduling | Yard Manager | — |
| Gate appointment quotas | Terminal Operator | Yard Manager |
| Customs clearance | Customs Authority | Customs Broker |
| Vessel stowage plan | Shipping Line | Terminal Operator |
| Truck routing inside terminal | Gate System / TOS | Yard Manager |

---
*Stakeholder analysis completed: 2026-08-26*
*Sources: DCSA Industry Blueprint, IPCSA Guidelines, industry operational documentation*
