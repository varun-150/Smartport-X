# SmartPortX — Integration Architecture

> **Version:** 1.0  
> **Status:** Active  
> **Last Updated:** 2026-08-26  
> **Scope:** Integration Service (`smartportx-integration`) — EDIFACT, IoT, Customs, Adapters, Data Import/Export, Event Flow

---

## Table of Contents

1. [Overview](#1-overview)
2. [Integration Service Responsibilities](#2-integration-service-responsibilities)
3. [EDIFACT Integration](#3-edifact-integration)
   - 3.1 [Supported Message Types](#31-supported-message-types)
   - 3.2 [Parsing Pipeline](#32-parsing-pipeline)
   - 3.3 [EDIFACT Domain Model](#33-edifact-domain-model)
   - 3.4 [Audit Trail](#34-audit-trail)
   - 3.5 [Outbound EDIFACT (Future)](#35-outbound-edifact-future)
4. [IoT Integration (Simulated)](#4-iot-integration-simulated)
   - 4.1 [Data Types](#41-data-types)
   - 4.2 [Simulation Mode](#42-simulation-mode)
   - 4.3 [Live Mode (Future)](#43-live-mode-future)
   - 4.4 [Data Normalization and Storage](#44-data-normalization-and-storage)
   - 4.5 [IoT Event Consumption](#45-iot-event-consumption)
5. [Customs Integration (Simulated)](#5-customs-integration-simulated)
   - 5.1 [Status Workflow](#51-status-workflow)
   - 5.2 [Gate-Out Validation](#52-gate-out-validation)
   - 5.3 [Event Publishing](#53-event-publishing)
   - 5.4 [Real Customs Integration (Future)](#54-real-customs-integration-future)
6. [External System Adapters](#6-external-system-adapters)
   - 6.1 [Adapter Pattern Design](#61-adapter-pattern-design)
   - 6.2 [Adapter Implementations](#62-adapter-implementations)
   - 6.3 [Adapter Registry](#63-adapter-registry)
   - 6.4 [Swap Strategy](#64-swap-strategy)
7. [Data Import / Export](#7-data-import--export)
   - 7.1 [CSV Import](#71-csv-import)
   - 7.2 [CSV Export](#72-csv-export)
   - 7.3 [File Handling](#73-file-handling)
8. [Integration Event Flow](#8-integration-event-flow)
   - 8.1 [Inbound Flow](#81-inbound-flow)
   - 8.2 [Outbound Flow](#82-outbound-flow)
   - 8.3 [Error Handling and Retry](#83-error-handling-and-retry)
   - 8.4 [Dead-Letter Queue](#84-dead-letter-queue)
   - 8.5 [Event Schema](#85-event-schema)
9. [API Endpoints](#9-api-endpoints)
10. [Configuration](#10-configuration)
11. [Testing Strategy](#11-testing-strategy)
12. [Future Roadmap](#12-future-roadmap)

---

## 1. Overview

The Integration Service (`smartportx-integration`) is the boundary layer of the SmartPortX
platform. It is the single service responsible for all communication between the internal
microservice ecosystem and external systems — EDI translators, IoT devices, customs authorities,
terminal operating systems, and port community systems.

The service follows an **adapter-based architecture** where each external system is represented by
a dedicated adapter interface. Implementations can be swapped — simulated for development and
demo, real for production — without touching business logic in other services.

```
┌─────────────────────────────────────────────────────────────┐
│                     External Systems                        │
│                                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ EDIFACT  │  │   IoT    │  │ Customs  │  │   TOS    │   │
│  │ (EDI)    │  │ (MQTT)   │  │ (API)    │  │ (Future) │   │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘   │
│       │              │              │              │         │
└───────┼──────────────┼──────────────┼──────────────┼─────────┘
        │              │              │              │
┌───────▼──────────────▼──────────────▼──────────────▼─────────┐
│                 smartportx-integration                        │
│                                                               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐     │
│  │Edifact   │  │Iot       │  │Customs   │  │Tos       │     │
│  │Adapter   │  │Adapter   │  │Adapter   │  │Adapter   │     │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘     │
│       │              │              │              │           │
│  ┌────▼──────────────▼──────────────▼──────────────▼─────┐   │
│  │              Integration Event Bus                     │   │
│  └──────────────────────┬────────────────────────────────┘   │
└─────────────────────────┼────────────────────────────────────┘
                          │
                    RabbitMQ Exchange
                          │
        ┌─────────────────┼─────────────────┐
        │                 │                 │
   ┌────▼────┐      ┌────▼────┐      ┌────▼────┐
   │  Yard   │      │  Gate   │      │Container│
   │ Service │      │ Service │      │ Service │
   └─────────┘      └─────────┘      └─────────┘
```

---

## 2. Integration Service Responsibilities

| Responsibility | Description |
|---|---|
| EDIFACT Parsing | Inbound EDIFACT messages parsed to domain events |
| IoT Ingestion | Sensor data received, normalized, stored, published |
| Customs Simulation | Customs status transitions managed and events emitted |
| Data Import | Bulk CSV imports for historical data migration |
| Data Export | CSV exports for yard utilization and gate reports |
| Adapter Management | Pluggable adapters for each external system |
| Dead-Letter Handling | Failed messages captured for manual review |
| Raw Audit Storage | Original EDIFACT payloads retained for compliance |

**Service Port:** 8086  
**Database Schema:** `smartportx_integration`  
**Message Broker:** RabbitMQ  

---

## 3. EDIFACT Integration

### 3.1 Supported Message Types

The Integration Service handles four EDIFACT message types covering the full container
lifecycle at a port terminal:

#### COPRAR — Container Discharge/Load Order

| Field | Value |
|---|---|
| **Direction** | Inbound (Shipping Line → Terminal) |
| **Purpose** | Instructs the terminal to discharge a container from a vessel or load a container onto a vessel |
| **Key Segments** | UNH, BGM, TDT, EQD, LOC, DTM, RFF |
| **Triggered Events** | `ContainerDischargeOrderReceived`, `ContainerLoadOrderReceived` |
| **Consumers** | Yard Service (pre-allocate yard position), Vessel Service (update stowage plan) |

```
Sender:    Shipping Line System
Receiver:  SmartPortX Integration Service
Flow:      Shipping line sends COPRAR → Integration parses → Yard pre-allocates slot
```

#### COARRI — Container Discharge/Load Report

| Field | Value |
|---|---|
| **Direction** | Outbound (Terminal → Shipping Line) |
| **Purpose** | Reports that a container has been discharged from or loaded onto a vessel |
| **Key Segments** | UNH, BGM, TDT, EQD, LOC, DTM, FTX |
| **Triggered Events** | `ContainerDischargeReported`, `ContainerLoadReported` |
| **Consumers** | Vessel Service (update stowage), Container Service (update status) |

```
Sender:    SmartPortX Integration Service
Receiver:  Shipping Line System
Flow:      Crane operation completes → Container status updated → Integration generates COARRI
```

#### CODECO — Container Gate-In/Gate-Out Report

| Field | Value |
|---|---|
| **Direction** | Outbound (Terminal → Carrier/Trucker) |
| **Purpose** | Reports container gate-in (arrival at terminal) or gate-out (departure from terminal) |
| **Key Segments** | UNH, BGM, GID, TDT, EQD, LOC, DTM |
| **Triggered Events** | `ContainerGateInReported`, `ContainerGateOutReported` |
| **Consumers** | Notification Service (notify carrier), Container Service (update location) |

```
Sender:    SmartPortX Integration Service
Receiver:  Carrier / Trucker System
Flow:      Gate transaction completes → Integration generates CODECO → Carrier notified
```

#### COPARN — Container Announcement

| Field | Value |
|---|---|
| **Direction** | Inbound (Carrier → Terminal) |
| **Purpose** | Announces a future container move (gate-in or gate-out announcement) |
| **Key Segments** | UNH, BGM, TDT, EQD, LOC, DTM, FTX |
| **Triggered Events** | `ContainerArrivalAnnounced`, `ContainerDepartureAnnounced` |
| **Consumers** | Yard Service (plan yard allocation), Gate Service (expect arrival) |

```
Sender:    Carrier System
Receiver:  SmartPortX Integration Service
Flow:      Carrier announces container → Integration parses → Yard plans slot
```

### 3.2 Parsing Pipeline

The EDIFACT parsing pipeline converts raw EDIFACT messages into internal domain events
through a multi-stage process:

```
Raw EDIFACT
    │
    ▼
┌──────────────────────────┐
│  Stage 1: Segmentation   │  Split EDIFACT into segments (UNH, BGM, EQD, etc.)
└──────────────┬───────────┘
               │
               ▼
┌──────────────────────────┐
│  Stage 2: Tokenization   │  Split segments into data elements and component elements
└──────────────┬───────────┘
               │
               ▼
┌──────────────────────────┐
│  Stage 3: Validation     │  Validate message structure, check mandatory segments
└──────────────┬───────────┘
               │
               ▼
┌──────────────────────────┐
│  Stage 4: Mapping        │  Map EDIFACT segments to domain model objects
└──────────────┬───────────┘
               │
               ▼
┌──────────────────────────┐
│  Stage 5: Enrichment     │  Add metadata (timestamp, source, correlation ID)
└──────────────┬───────────┘
               │
               ▼
┌──────────────────────────┐
│  Stage 6: Publication    │  Publish domain event to RabbitMQ
└──────────────────────────┘
```

**Parser Implementation:**

```java
@Service
public class EdifactParser {

    public ParsedEdifactMessage parse(String rawEdifact, EdifactMessageType type) {
        // Stage 1: Segment into discrete EDIFACT segments
        List<EdifactSegment> segments = segmenter.segment(rawEdifact);

        // Stage 2: Validate message structure
        ValidationResult validation = validator.validate(segments, type);
        if (!validation.isValid()) {
            throw new EdifactValidationException(validation.getErrors());
        }

        // Stage 3: Map segments to domain model
        EdifactMessage message = mapper.map(segments, type);

        // Stage 4: Enrich with metadata
        message.setReceivedAt(Instant.now());
        message.setCorrelationId(UUID.randomUUID().toString());
        message.setRawPayload(rawEdifact);

        return message;
    }
}
```

**Spring Integration Configuration:**

```java
@Configuration
public class EdifactIntegrationConfig {

    @Bean
    public IntegrationFlow edifactInboundFlow() {
        return IntegrationFlow
            .from("edifactInboundChannel")
            .handle(edifactService, "processMessage")
            .get();
    }

    @Bean
    public MessageChannel edifactInboundChannel() {
        return MessageChannels.direct("edifactInboundChannel").get();
    }
}
```

### 3.3 EDIFACT Domain Model

Internal domain model represents EDIFACT concepts without tight coupling to EDIFACT syntax:

```
EdifactMessage
├── messageId: String
├── messageType: EdifactMessageType (COPRAR, COARRI, CODECO, COPARN)
├── sender: String
├── receiver: String
├── timestamp: Instant
├── correlationId: String
├── rawPayload: String
├── segments: List<EdifactSegment>
│
├── ContainerInfo
│   ├── containerNumber: String
│   ├── isoCode: String (20G, 40H, etc.)
│   ├── tareWeight: BigDecimal
│   └── seals: List<SealInfo>
│
├── TransportInfo
│   ├── vesselName: String
│   ├── voyageNumber: String
│   ├── carrierCode: String
│   └── transportMode: String
│
├── LocationInfo
│   ├── locationName: String
│   ├── unLocationCode: String
│   └── portCode: String
│
└── Timestamps
    ├── estimatedArrival: Instant
    ├── estimatedDeparture: Instant
    └── actualArrival: Instant
```

### 3.4 Audit Trail

Every EDIFACT message — whether successfully parsed or not — is stored in the
`edifact_messages` table for audit and compliance purposes.

```sql
CREATE TABLE edifact_messages (
    id              BIGSERIAL PRIMARY KEY,
    message_id      VARCHAR(50) NOT NULL,
    message_type    VARCHAR(10) NOT NULL,
    sender          VARCHAR(50) NOT NULL,
    receiver        VARCHAR(50) NOT NULL,
    direction       VARCHAR(10) NOT NULL,       -- INBOUND or OUTBOUND
    raw_payload     TEXT NOT NULL,               -- Original EDIFACT content
    parsed_data     JSONB,                       -- Parsed domain model (JSON)
    status          VARCHAR(20) NOT NULL,        -- RECEIVED, PARSED, PROCESSED, FAILED
    error_message   TEXT,
    correlation_id  VARCHAR(36),
    received_at     TIMESTAMP NOT NULL,
    processed_at    TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_edifact_messages_type ON edifact_messages(message_type);
CREATE INDEX idx_edifact_messages_status ON edifact_messages(status);
CREATE INDEX idx_edifact_messages_correlation ON edifact_messages(correlation_id);
```

**Audit Policy:**
- Raw EDIFACT payloads stored indefinitely (regulatory requirement)
- Parsed data retained for 7 years
- Failed messages flagged with error details for manual review
- Queryable via REST API for debugging and compliance audits

### 3.5 Outbound EDIFACT (Future)

Outbound EDIFACT generation (sending EDIFACT messages to external systems) is planned
but **excluded from MVP scope** per design decision D-008.

**Planned Outbound Messages:**

| Message | Direction | Purpose |
|---|---|---|
| COARRI | Terminal → Shipping Line | Discharge/load confirmation |
| CODECO | Terminal → Carrier | Gate-in/gate-out report |
| IFTMIN | Terminal → Carrier | Transport instruction |
| IFTSTA | Terminal → Carrier | Transport status update |

**Implementation Approach (Future):**
- Outbound adapter interfaces already defined (see Section 6)
- Event listeners in Integration Service will consume domain events
- Transform domain event → EDIFACT segments → serialize → send
- Outbound queue for reliable delivery with retry

---

## 4. IoT Integration (Simulated)

### 4.1 Data Types

The IoT subsystem ingests five types of sensor data from containers and terminal equipment:

| Sensor Type | Unit | Range | Sampling Rate | Description |
|---|---|---|---|---|
| GPS Location | lat/lng | Global | 30s | Container or vehicle position |
| Temperature | °C | -40 to +60 | 60s | Reefer container internal temp |
| Humidity | % RH | 0–100 | 120s | Container internal humidity |
| Door Sensor | open/closed | Binary | 5s (on change) | Container door state |
| Battery Level | % | 0–100 | 300s | Sensor battery remaining |

**Additional Metadata per Reading:**
- Container number or device ID
- Timestamp (UTC)
- Signal strength (RSSI)
- Battery voltage
- Firmware version

### 4.2 Simulation Mode

Simulation mode (default) generates realistic IoT sensor data for development and demo
purposes without requiring physical hardware.

**Python Simulator Script:**

```python
# smartportx-integration/scripts/iot_simulator.py
#
# Generates realistic IoT sensor data with:
# - Gaussian noise on numeric sensors
# - Realistic battery drain (exponential decay)
# - Signal strength variation (distance-dependent)
# - Intermittent connectivity loss (Markov chain)
# - Reboot events (battery replacement simulation)

import json
import random
import time
import math
from datetime import datetime, timezone

class ContainerSimulator:
    def __init__(self, container_number, initial_lat, initial_lng):
        self.container_number = container_number
        self.lat = initial_lat
        self.lng = initial_lng
        self.battery = 100.0
        self.temperature = 4.0
        self.humidity = 45.0
        self.door_open = False
        self.connected = True
        self.firmware_version = "2.4.1"

    def tick(self, delta_seconds):
        """Advance simulation by delta_seconds."""
        # Battery drain: ~0.001% per second when connected
        self.battery -= 0.001 * delta_seconds
        if self.battery < 0:
            self.battery = 0

        # Battery replacement event (5% chance every hour)
        if self.battery < 10 and random.random() < 0.0014:
            self.battery = 100.0

        # Temperature drift: slow random walk around setpoint
        self.temperature += random.gauss(0, 0.05)
        self.temperature = max(-40, min(60, self.temperature))

        # Humidity: correlated with temperature
        self.humidity += random.gauss(0, 0.2)
        self.humidity = max(0, min(100, self.humidity))

        # Connectivity: Markov chain (95% uptime)
        if self.connected:
            self.connected = random.random() > 0.005
        else:
            self.connected = random.random() > 0.1

        return self.to_reading()

    def to_reading(self):
        return {
            "containerNumber": self.container_number,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "gps": {
                "latitude": self.lat + random.gauss(0, 0.00001),
                "longitude": self.lng + random.gauss(0, 0.00001),
                "accuracy": random.uniform(2.0, 15.0)
            },
            "temperature": {
                "value": round(self.temperature + random.gauss(0, 0.1), 2),
                "unit": "CELSIUS"
            },
            "humidity": {
                "value": round(self.humidity + random.gauss(0, 0.5), 2),
                "unit": "PERCENT"
            },
            "doorSensor": {
                "open": self.door_open
            },
            "battery": {
                "level": round(self.battery, 2),
                "voltage": round(3.0 + (self.battery / 100) * 0.6, 3)
            },
            "signalStrength": {
                "rssi": -60 + random.randint(-30, 10) if self.connected else None,
                "connected": self.connected
            },
            "firmwareVersion": self.firmware_version
        }
```

**Simulation Configuration:**

```yaml
# application-docker.yml (Integration Service)
smartportx:
  iot:
    mode: simulation
    simulation:
      container-count: 50
      tick-interval-seconds: 30
      noise-enabled: true
      connectivity-loss-rate: 0.005
      battery-drain-rate: 0.001
      mqtt-topic-prefix: smartportx/iot
```

### 4.3 Live Mode (Future)

Live mode connects to a real MQTT broker accepting data from physical IoT devices.

**Planned MQTT Topics:**

```
smartportx/iot/{containerNumber}/gps
smartportx/iot/{containerNumber}/temperature
smartportx/iot/{containerNumber}/humidity
smartportx/iot/{containerNumber}/door
smartportx/iot/{containerNumber}/battery
```

**Live Mode Configuration (Future):**

```yaml
smartportx:
  iot:
    mode: live
    mqtt:
      broker-url: tcp://mqtt-broker:1883
      client-id: smartportx-integration
      username: ${MQTT_USERNAME}
      password: ${MQTT_PASSWORD}
      topics:
        - smartportx/iot/+/gps
        - smartportx/iot/+/temperature
        - smartportx/iot/+/humidity
        - smartportx/iot/+/door
        - smartportx/iot/+/battery
```

### 4.4 Data Normalization and Storage

All IoT data — whether from simulation or live sensors — is normalized to a common
format and stored in the `iot_readings` table.

```sql
CREATE TABLE iot_readings (
    id                BIGSERIAL PRIMARY KEY,
    container_number  VARCHAR(11) NOT NULL,
    sensor_type       VARCHAR(20) NOT NULL,      -- GPS, TEMPERATURE, HUMIDITY, DOOR, BATTERY
    value_numeric     DOUBLE PRECISION,           -- For numeric sensors
    value_text        VARCHAR(50),                -- For categorical sensors (door open/closed)
    unit              VARCHAR(20),                -- CELSIUS, PERCENT, etc.
    latitude          DOUBLE PRECISION,           -- GPS-specific
    longitude         DOUBLE PRECISION,           -- GPS-specific
    accuracy_meters   DOUBLE PRECISION,           -- GPS accuracy
    signal_strength   INTEGER,                    -- RSSI value
    battery_level     DOUBLE PRECISION,           -- Device battery %
    firmware_version  VARCHAR(20),
    raw_payload       JSONB,                      -- Original sensor data
    received_at       TIMESTAMP NOT NULL,
    device_timestamp  TIMESTAMP NOT NULL,         -- When sensor took reading
    created_at        TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_iot_container ON iot_readings(container_number);
CREATE INDEX idx_iot_sensor_type ON iot_readings(sensor_type);
CREATE INDEX idx_iot_received_at ON iot_readings(received_at);
CREATE INDEX idx_iot_container_time ON iot_readings(container_number, device_timestamp DESC);
```

**Normalization Process:**

```
Raw Sensor Data
    │
    ▼
┌──────────────────────────┐
│  Validate Payload        │  Schema validation, null checks
└──────────────┬───────────┘
               │
               ▼
┌──────────────────────────┐
│  Normalize to Common     │  Convert to internal IoTReading model
│  IoTReading Model        │
└──────────────┬───────────┘
               │
               ▼
┌──────────────────────────┐
│  Persist to iot_readings │  Write to database
└──────────────┬───────────┘
               │
               ▼
┌──────────────────────────┐
│  Publish IoTSensorData   │  Event for other services
│  Event                   │
└──────────────────────────┘
```

### 4.5 IoT Event Consumption

The **Yard Service** subscribes to IoT events for real-time container location updates:

```
Integration Service                Yard Service
       │                               │
       │──── IoTSensorData (GPS) ─────▶│
       │                               │──▶ Update container yard position
       │                               │──▶ Check if container is in expected zone
       │                               │──▶ Trigger alert if out-of-zone
       │                               │
       │──── IoTSensorData (Temp) ────▶│
       │                               │──▶ Check reefer temperature range
       │                               │──▶ Alert if out-of-range
```

**Event Payload:**

```json
{
  "eventType": "IoTSensorData",
  "timestamp": "2026-08-26T10:30:00Z",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "source": "smartportx-integration",
  "data": {
    "containerNumber": "MSKU7654321",
    "sensorType": "GPS",
    "latitude": 1.2644,
    "longitude": 103.8222,
    "accuracy": 5.2,
    "deviceTimestamp": "2026-08-26T10:29:45Z",
    "signalStrength": -58
  }
}
```

---

## 5. Customs Integration (Simulated)

### 5.1 Status Workflow

The customs workflow is simulated within the Integration Service. Each container
passes through a defined status lifecycle:

```
                    ┌─────────────┐
                    │   PENDING   │
                    │ (Submitted) │
                    └──────┬──────┘
                           │
                           ▼
                    ┌─────────────┐
                    │ UNDER_REVIEW│
                    │ (Processing)│
                    └──────┬──────┘
                           │
                    ┌──────┴──────┐
                    │             │
                    ▼             ▼
            ┌──────────┐  ┌──────────┐
            │ CLEARED  │  │   HELD   │
            │(Approved)│  │(On Hold) │
            └────┬─────┘  └────┬─────┘
                 │             │
                 │    ┌────────┘
                 │    │ (if issue resolved)
                 ▼    ▼
            ┌──────────┐
            │ RELEASED │
            │(Complete)│
            └──────────┘
```

**Status Transitions:**

| From | To | Trigger | Duration (Simulated) |
|---|---|---|---|
| PENDING | UNDER_REVIEW | Auto (on submission) | Immediate |
| UNDER_REVIEW | CLEARED | Auto (random: 70% chance) | 2–8 hours |
| UNDER_REVIEW | HELD | Auto (random: 30% chance) | 2–8 hours |
| HELD | UNDER_REVIEW | Manual (re-review request) | N/A |
| CLEARED | RELEASED | Auto (after clearance) | 1 hour |

**Simulated Behavior:**
- Containers submitted with COPRAR or arrival events automatically get customs status PENDING
- After simulated review period (2–8 hours), randomly cleared or held
- Held containers can be "manually" re-reviewed via API
- Cleared containers move to RELEASED status
- Gate Service checks RELEASED status before allowing gate-out (per SR-007.4)

### 5.2 Gate-Out Validation

Gate Service enforces customs clearance before allowing container exit:

```
Gate Service                          Integration Service
     │                                       │
     │── GateOutRequest(containerNumber) ──▶│
     │                                       │── Check customs status
     │                                       │
     │◀── CustomsStatus: RELEASED ──────────│  (proceed)
     │    OR                                 │
     │◀── CustomsStatus: HELD ──────────────│  (reject)
     │                                       │
```

**Validation Rule (SR-007.4):**

```java
@Service
public class GateOutValidationService {

    public GateOutValidationResult validate(String containerNumber) {
        // Check customs status
        CustomsStatus customsStatus = customsClient.getStatus(containerNumber);

        if (customsStatus != CustomsStatus.RELEASED) {
            return GateOutValidationResult.rejected(
                "Container not cleared by customs. Current status: " + customsStatus
            );
        }

        return GateOutValidationResult.approved();
    }
}
```

### 5.3 Event Publishing

Customs status changes publish events for other services:

```json
{
  "eventType": "CustomsStatusUpdated",
  "timestamp": "2026-08-26T14:30:00Z",
  "correlationId": "customs-uuid-12345",
  "source": "smartportx-integration",
  "data": {
    "containerNumber": "MSKU7654321",
    "previousStatus": "UNDER_REVIEW",
    "newStatus": "CLEARED",
    "customsReference": "CUS-2026-001234",
    "reviewedBy": "SIMULATED_OFFICER",
    "remarks": "Documentation verified. Cleared for release."
  }
}
```

**Event Consumers:**
- **Gate Service:** Updates gate-out eligibility cache
- **Container Service:** Updates container customs status
- **Notification Service:** Alerts stakeholder of customs clearance

### 5.4 Real Customs Integration (Future)

Real customs system integration is country-specific and **excluded from MVP scope**
per design decision D-005.

**Future Implementation Considerations:**

| Country | System | Protocol | Complexity |
|---|---|---|---|
| Singapore | TradeNet | SOAP/XML | High |
| Netherlands | MIRS | HTTPS REST | Medium |
| UAE | Mirsal 2 | Proprietary API | High |
| India | ICEGATE | EDIFACT/Gateway | High |

Each country integration would require a dedicated CustomsAdapter implementation,
country-specific authentication, and regulatory compliance review.

---

## 6. External System Adapters

### 6.1 Adapter Pattern Design

The Integration Service uses the **Adapter Pattern** to decouple business logic from
external system specifics. Each adapter has a well-defined interface; implementations
can be swapped based on deployment mode.

```
                    ┌─────────────────────┐
                    │   Adapter Interface  │
                    │   (Contract)         │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
     ┌────────▼───────┐ ┌─────▼───────┐ ┌──────▼──────┐
     │ Simulated      │ │ Live        │ │ Mock        │
     │ Implementation │ │ Implem.     │ │ (Testing)   │
     └────────────────┘ └─────────────┘ └─────────────┘
```

### 6.2 Adapter Implementations

#### EdifactAdapter

```java
public interface EdifactAdapter {

    /**
     * Parse an inbound EDIFACT message into a domain model.
     */
    ParsedEdifactMessage parse(String rawEdifact, EdifactMessageType type);

    /**
     * Generate an outbound EDIFACT message from a domain event.
     * (Future — not in MVP scope)
     */
    String generate(EdifactMessage message);

    /**
     * Validate EDIFACT message structure.
     */
    ValidationResult validate(String rawEdifact);
}

@Component
@Profile("docker")
public class SimulatedEdifactAdapter implements EdifactAdapter {

    private final EdifactParser parser;

    @Override
    public ParsedEdifactMessage parse(String rawEdifact, EdifactMessageType type) {
        return parser.parse(rawEdifact, type);
    }

    @Override
    public String generate(EdifactMessage message) {
        throw new UnsupportedOperationException(
            "Outbound EDIFACT generation not in MVP scope (D-008)"
        );
    }

    @Override
    public ValidationResult validate(String rawEdifact) {
        return parser.validate(rawEdifact);
    }
}
```

#### IotAdapter

```java
public interface IotAdapter {

    /**
     * Ingest sensor data from an IoT device (or simulator).
     */
    IoTReading ingest(IoTSensorPayload payload);

    /**
     * Publish a command to an IoT device.
     * (Future — not in MVP scope)
     */
    void publishCommand(String containerNumber, IoTCommand command);

    /**
     * Check connectivity status of IoT devices.
     */
    DeviceStatus checkDeviceStatus(String containerNumber);
}
```

#### CustomsAdapter

```java
public interface CustomsAdapter {

    /**
     * Submit a container declaration to customs.
     */
    CustomsDeclarationResult submitDeclaration(CustomsDeclaration declaration);

    /**
     * Query current customs status for a container.
     */
    CustomsStatus getStatus(String containerNumber);

    /**
     * Request re-review for a held container.
     */
    RereviewResult requestRereview(String containerNumber, String reason);
}
```

#### TosAdapter (Placeholder)

```java
public interface TosAdapter {

    /**
     * Send a work order to the Terminal Operating System.
     * Placeholder for future TOS integration.
     */
    WorkOrderResult sendWorkOrder(WorkOrder workOrder);

    /**
     * Query TOS for equipment status.
     */
    EquipmentStatus getEquipmentStatus(String equipmentId);

    /**
     * Update stowage position in TOS.
     */
    StowageUpdateResult updateStowage(String vesselId, StowagePosition position);
}
```

#### PcsAdapter (Placeholder)

```java
public interface PcsAdapter {

    /**
     * Submit a vessel call notification to the Port Community System.
     * Placeholder for future PCS integration.
     */
    VesselCallResult notifyVesselCall(VesselCallNotification notification);

    /**
     * Submit a berth request to PCS.
     */
    BerthRequestResult requestBerth(BerthRequest request);

    /**
     * Query port schedule from PCS.
     */
    List<PortCall> getPortSchedule(DateRange range);
}
```

### 6.3 Adapter Registry

Adapters are registered in the Spring context and resolved by type:

```java
@Component
public class AdapterRegistry {

    private final Map<Class<?>, Object> adapters = new ConcurrentHashMap<>();

    public <T> void register(Class<T> adapterType, T implementation) {
        adapters.put(adapterType, implementation);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAdapter(Class<T> adapterType) {
        T adapter = (T) adapters.get(adapterType);
        if (adapter == null) {
            throw new AdapterNotFoundException(
                "No adapter registered for: " + adapterType.getSimpleName()
            );
        }
        return adapter;
    }
}
```

### 6.4 Swap Strategy

Adapters are selected via Spring Profiles:

| Profile | EdifactAdapter | IotAdapter | CustomsAdapter |
|---|---|---|---|
| `local` | SimulatedEdifactAdapter | SimulatedIotAdapter | SimulatedCustomsAdapter |
| `docker` | SimulatedEdifactAdapter | SimulatedIotAdapter | SimulatedCustomsAdapter |
| `production` | LiveEdifactAdapter | LiveIotAdapter | LiveCustomsAdapter |

```java
@Configuration
@Profile("local")
public class LocalAdapterConfig {

    @Bean
    public EdifactAdapter edifactAdapter() {
        return new SimulatedEdifactAdapter();
    }

    @Bean
    public IotAdapter iotAdapter() {
        return new SimulatedIotAdapter();
    }

    @Bean
    public CustomsAdapter customsAdapter() {
        return new SimulatedCustomsAdapter();
    }
}
```

To swap an implementation:
1. Create new adapter class implementing the interface
2. Add `@Component` with appropriate `@Profile` annotation
3. Register in the profile-specific configuration class
4. No changes needed to consuming business logic

---

## 7. Data Import / Export

### 7.1 CSV Import

Bulk CSV import supports historical data migration for containers and related entities.

**Endpoint:** `POST /api/v1/integration/import/containers`

**CSV Format:**

```csv
container_number,iso_code,carrier,shipper,consignee,origin,destination,weight_kg,commodity,customs_status
MSKU7654321,40HC,MAEU,Acme Corp,Beta Ltd,SGSIN,NLRTM,28500,Electronics,CLEARED
TCLU8765432,20GP,HLBU,Gamma Inc,Delta LLC,AEJEA,DEHAM,15200,Textiles,PENDING
```

**Import Processing:**

```
CSV Upload
    │
    ▼
┌──────────────────────────┐
│  Validate File Format    │  Check CSV structure, required columns
└──────────────┬───────────┘
               │
               ▼
┌──────────────────────────┐
│  Parse Rows              │  Streaming parser for large files
└──────────────┬───────────┘
               │
               ▼
┌──────────────────────────┐
│  Validate Data           │  Row-level validation, data type checks
└──────────────┬───────────┘
               │
               ▼
┌──────────────────────────┐
│  Deduplicate             │  Check existing containers by number
└──────────────┬───────────┘
               │
               ▼
┌──────────────────────────┐
│  Bulk Insert             │  Batch insert into container table
└──────────────┬───────────┘
               │
               ▼
┌──────────────────────────┐
│  Publish Events          │  ContainerCreated events for each new record
└──────────────┬───────────┘
               │
               ▼
┌──────────────────────────┐
│  Return Import Summary   │  Total rows, successful, failed, duplicates
└──────────────────────────┘
```

**Import Response:**

```json
{
  "importId": "import-uuid-12345",
  "status": "COMPLETED",
  "summary": {
    "totalRows": 1542,
    "successful": 1530,
    "failed": 5,
    "duplicates": 7
  },
  "errors": [
    {
      "row": 45,
      "containerNumber": "INVALID123",
      "error": "Invalid container number format"
    }
  ],
  "startedAt": "2026-08-26T10:00:00Z",
  "completedAt": "2026-08-26T10:02:15Z"
}
```

### 7.2 CSV Export

Streaming CSV export for reports without loading entire result set into memory.

**Endpoint:** `GET /api/v1/integration/export/{report-type}`

**Supported Report Types:**

| Report Type | Description | Content |
|---|---|---|
| `yard-utilization` | Yard occupancy by zone over time | Zone, date, slots used, slots total, utilization % |
| `gate-transactions` | Gate-in/gate-out history | Container, direction, timestamp, carrier, driver |
| `container-status` | Container lifecycle summary | Container, status history, timestamps |
| `vessel-calls` | Vessel arrival/departure log | Vessel, arrival, departure, berths |
| `customs-summary` | Customs processing statistics | Container, status, duration, result |

**Export Response:**
- Content-Type: `text/csv`
- Content-Disposition: `attachment; filename="yard-utilization-2026-08-26.csv"`
- Streaming via Spring MVC `StreamingResponseBody`

### 7.3 File Handling

| Property | Import | Export |
|---|---|---|
| File Size Limit | 10 MB | Unlimited (streaming) |
| Format | CSV only | CSV only |
| Encoding | UTF-8 | UTF-8 |
| Delimiter | Comma | Comma |
| Header Row | Required | Always included |
| Multipart | Yes | N/A |
| Streaming | Yes (for large files) | Yes (chunked response) |

---

## 8. Integration Event Flow

### 8.1 Inbound Flow

External systems push data to Integration Service, which parses and publishes events.

```
┌────────────┐     ┌─────────────────────┐     ┌──────────┐     ┌────────────────┐
│ External   │────▶│ Integration Service  │────▶│ RabbitMQ │────▶│ Other          │
│ System     │     │                     │     │ Exchange │     │ Services       │
└────────────┘     │ 1. Receive payload  │     └──────────┘     └────────────────┘
                   │ 2. Route to adapter │
                   │ 3. Parse via adapter│
                   │ 4. Store raw payload│
                   │ 5. Persist parsed   │
                   │ 6. Publish event    │
                   └─────────────────────┘
```

**Inbound Examples:**

| Source | Protocol | Message | Event Published |
|---|---|---|---|
| Shipping Line | EDIFACT | COPRAR | ContainerDischargeOrderReceived |
| Carrier | EDIFACT | COPARN | ContainerArrivalAnnounced |
| IoT Gateway | MQTT | Sensor Reading | IoTSensorData |
| Customs System | REST | Status Update | CustomsStatusUpdated |

### 8.2 Outbound Flow

Internal services publish events that Integration Service transforms and sends externally.

```
┌────────────────┐     ┌──────────┐     ┌─────────────────────┐     ┌────────────┐
│ Internal       │────▶│ RabbitMQ │────▶│ Integration Service  │────▶│ External   │
│ Service        │     │ Exchange │     │                     │     │ System     │
└────────────────┘     └──────────┘     │ 1. Consume event    │     └────────────┘
                                        │ 2. Route to adapter │
                                        │ 3. Transform via    │
                                        │    adapter          │
                                        │ 4. Send to external │
                                        │ 5. Log result       │
                                        └─────────────────────┘
```

**Outbound Examples:**

| Trigger Event | Transformation | Destination |
|---|---|---|
| ContainerDischargeCompleted | Generate COARRI | Shipping Line |
| ContainerGateOutCompleted | Generate CODECO | Carrier |
| BerthAllocated | Send to TOS | Terminal OS |
| VesselArrivalConfirmed | Send to PCS | Port Community |

### 8.3 Error Handling and Retry

Failed integrations follow a retry-then-dead-letter pattern:

```
Message Received
    │
    ▼
┌──────────────────┐
│  Process Message  │
└────────┬─────────┘
         │
    ┌────┴────┐
    │ Success? │
    └────┬────┘
    Yes  │  No
    │    │
    │    ▼
    │  ┌──────────────────┐
    │  │ Retry (attempt 1)│
    │  └────────┬─────────┘
    │      ┌────┴────┐
    │      │ Success? │
    │      └────┬────┘
    │      Yes  │  No
    │      │    │
    │      │    ▼
    │      │  ┌──────────────────┐
    │      │  │ Retry (attempt 2)│
    │      │  └────────┬─────────┘
    │      │      ┌────┴────┐
    │      │      │ Success? │
    │      │      └────┬────┘
    │      │      Yes  │  No
    │      │      │    │
    │      │      │    ▼
    │      │      │  ┌──────────────────┐
    │      │      │  │ Retry (attempt 3)│
    │      │      │  └────────┬─────────┘
    │      │      │      ┌────┴────┐
    │      │      │      │ Success? │
    │      │      │      └────┬────┘
    │      │      │      Yes  │  No
    │      │      │      │    │
    │      │      │      │    ▼
    │      │      │      │  Dead-Letter Queue
    │      │      │      │
    ▼      ▼      ▼      ▼
┌──────────────────────┐
│  Log + Ack Message   │
└──────────────────────┘
```

**Retry Configuration:**

```yaml
smartportx:
  integration:
    retry:
      max-attempts: 3
      initial-delay-ms: 1000
      max-delay-ms: 30000
      multiplier: 2.0
      dead-letter-exchange: smartportx.dlx
      dead-letter-queue: smartportx.dlq.integration
```

**Spring Retry Integration:**

```java
@Service
@Slf4j
public class IntegrationEventProcessor {

    @Retryable(
        retryFor = {IntegrationException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 30000)
    )
    public void processInbound(String payload, String messageType) {
        EdifactMessageType type = EdifactMessageType.valueOf(messageType);
        ParsedEdifactMessage parsed = edifactAdapter.parse(payload, type);
        integrationService.storeAndPublish(parsed);
    }

    @Recover
    public void recoverProcessInbound(IntegrationException e, String payload, String messageType) {
        log.error("Failed to process {} after 3 attempts. Sending to DLQ.", messageType);
        deadLetterService.sendToDeadLetter(payload, messageType, e.getMessage());
    }
}
```

### 8.4 Dead-Letter Queue

Messages that fail all retry attempts land in the Dead-Letter Queue (DLQ) for manual review.

```sql
CREATE TABLE dead_letter_messages (
    id              BIGSERIAL PRIMARY KEY,
    original_queue  VARCHAR(100) NOT NULL,
    message_type    VARCHAR(50) NOT NULL,
    payload         TEXT NOT NULL,
    error_message   TEXT,
    retry_count     INTEGER NOT NULL,
    first_attempted TIMESTAMP NOT NULL,
    last_attempted  TIMESTAMP NOT NULL,
    status          VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, REVIEWED, RETRIED, DISCARDED
    reviewed_by     VARCHAR(50),
    reviewed_at     TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW()
);
```

**DLQ Management API:**

| Endpoint | Method | Description |
|---|---|---|
| `/api/v1/integration/dlq` | GET | List dead-letter messages (paginated) |
| `/api/v1/integration/dlq/{id}` | GET | Get specific dead-letter message details |
| `/api/v1/integration/dlq/{id}/retry` | POST | Manually retry a dead-letter message |
| `/api/v1/integration/dlq/{id}/discard` | POST | Mark message as discarded |

### 8.5 Event Schema

All events published by Integration Service follow the standard SmartPortX event envelope:

```json
{
  "eventType": "string",
  "eventVersion": "1.0",
  "timestamp": "ISO-8601",
  "correlationId": "UUID",
  "source": "smartportx-integration",
  "data": {
    // Event-specific payload
  },
  "metadata": {
    "rawSource": "edifact|mqtt|simulated|rest",
    "adapterUsed": "EdifactAdapter|IotAdapter|CustomsAdapter",
    "processingTimeMs": 123,
    "retryCount": 0
  }
}
```

---

## 9. API Endpoints

| Method | Path | Description | Request | Response |
|---|---|---|---|---|
| POST | `/api/v1/integration/edifact/inbound` | Submit EDIFACT message | Raw EDIFACT body | Parsed message result |
| GET | `/api/v1/integration/edifact/messages` | List EDIFACT messages | Query params (type, status, date range) | Paginated list |
| GET | `/api/v1/integration/edifact/messages/{id}` | Get EDIFACT message detail | — | Full message with raw payload |
| POST | `/api/v1/integration/iot/ingest` | Ingest IoT sensor data | IoTReading JSON | Ingestion confirmation |
| GET | `/api/v1/integration/iot/readings` | Query IoT readings | Query params (container, type, date range) | Paginated list |
| GET | `/api/v1/integration/iot/status/{containerNumber}` | Get device status | — | Device status with battery, signal |
| POST | `/api/v1/integration/customs/submit` | Submit customs declaration | CustomsDeclaration JSON | Declaration result |
| GET | `/api/v1/integration/customs/status/{containerNumber}` | Get customs status | — | Current customs status |
| POST | `/api/v1/integration/customs/rereview/{containerNumber}` | Request re-review | Reason JSON | Rereview result |
| POST | `/api/v1/integration/import/containers` | Bulk CSV import | Multipart CSV file | Import summary |
| GET | `/api/v1/integration/export/{report-type}` | Export report as CSV | Query params (date range, filters) | Streaming CSV |
| GET | `/api/v1/integration/dlq` | List dead-letter messages | Query params (status, date range) | Paginated list |
| POST | `/api/v1/integration/dlq/{id}/retry` | Retry dead-letter message | — | Retry result |
| POST | `/api/v1/integration/dlq/{id}/discard` | Discard dead-letter message | — | Confirmation |

---

## 10. Configuration

**application.yml (base):**

```yaml
server:
  port: ${SERVICE_PORT:8086}

spring:
  application:
    name: smartportx-integration
  datasource:
    url: jdbc:${DATABASE_URL:postgresql://localhost:5432/smartportx_integration}
    username: ${DATABASE_USERNAME:smartportx}
    password: ${DATABASE_PASSWORD:smartportx}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:guest}
    password: ${RABBITMQ_PASSWORD:guest}

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_CLIENT_SERVICEURL_DEFAULTZONE:http://localhost:8761/eureka}

smartportx:
  iot:
    mode: ${IOT_MODE:simulation}
    simulation:
      container-count: ${SIM_CONTAINER_COUNT:50}
      tick-interval-seconds: ${SIM_TICK_INTERVAL:30}
  integration:
    retry:
      max-attempts: ${RETRY_MAX_ATTEMPTS:3}
      dead-letter-exchange: ${DL_EXCHANGE:smartportx.dlx}
      dead-letter-queue: ${DL_QUEUE:smartportx.dlq.integration}
  customs:
    review-delay-min-hours: ${CUSTOMS_REVIEW_MIN:2}
    review-delay-max-hours: ${CUSTOMS_REVIEW_MAX:8}
    clearance-rate: ${CUSTOMS_CLEARANCE_RATE:0.7}
```

---

## 11. Testing Strategy

| Test Type | Scope | Tools |
|---|---|---|
| Unit Tests | Parser, adapters, transformers | JUnit 5, Mockito |
| Integration Tests | EDIFACT parsing end-to-end | Testcontainers (PostgreSQL) |
| Contract Tests | Event schema compatibility | Spring Cloud Contract |
| Simulation Tests | IoT data generation accuracy | JUnit + assertions on distributions |
| Import/Export Tests | CSV parsing, file limits | JUnit + test CSV fixtures |
| API Tests | REST endpoint validation | MockMvc + Testcontainers |

**Key Test Scenarios:**

- EDIFACT COPRAR parsing with valid and malformed messages
- IoT simulation produces readings within expected ranges
- Customs status transitions follow defined state machine
- CSV import rejects files over 10MB
- Dead-letter queue receives messages after 3 failed retries
- Adapter swap via profile changes without business logic changes

---

## 12. Future Roadmap

| Item | Priority | Dependencies | Status |
|---|---|---|---|
| Outbound EDIFACT generation (COARRI, CODECO) | High | TOS integration complete | Planned (D-008 deferred) |
| Live MQTT IoT integration | Medium | MQTT broker deployment | Planned |
| Real customs system integration | Low | Country-specific requirements | Planned (D-005 deferred) |
| TOS adapter implementation | Medium | TOS API documentation | Placeholder ready |
| PCS adapter implementation | Medium | PCS API documentation | Placeholder ready |
| EDIFACT message translation (UN/EDIFACT v2) | Low | Regulatory requirement | Not started |
| IoT device management API | Medium | Live IoT deployment | Not started |
| Customs pre-clearance API | Low | Customs authority approval | Not started |

---

*End of Integration Architecture Document*
