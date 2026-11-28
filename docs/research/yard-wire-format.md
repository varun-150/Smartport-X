# Yard Service — Wire-Format Reference

Companion to `docs/plans/PLAN.md` (when written) for the `smartportx-dashboard` React client. Source-verified against the Spring Boot implementation.

Classification key: **FACT** = read directly from the cited file in this repository. Nothing below is inferred.

## 1. JSON conventions

- **FACT** — Jackson camelCase mapping: Java record field names serialized verbatim (`containerId`, `utilizationRate`). No `@JsonProperty` renames found in the cited DTOs.
- **FACT** — `java.time.Instant` serializes as ISO-8601 (e.g. `2026-09-14T03:04:05Z`); `UUID` as string (`org.springframework.boot` autoconfigured JSR310 + Jackson).
- **FACT** — Enums serialize via `.name()`, so the JSON value is the exact constant string (e.g. `"COMPLETED"`).
- **FACT** — Position format in `com.smartportx.yard.domain.YardPosition.format`: `String.format("%s-B%02d-R%02d-T%02d", blockCode, bay, row, tier)` → e.g. `A-B03-R04-T02`.

## 2. Enum wire values

| Enum | File | Values |
|---|---|---|
| `BlockType` | `smartportx-yard/.../domain/BlockType.java` | `EXPORT`, `IMPORT`, `TRANSIT`, `REFRIGERATED`, `DANGEROUS`, `EMPTY` |
| `EquipmentType` | `smartportx-yard/.../domain/EquipmentType.java` | `RTG`, `ASC` |
| `EquipmentStatus` | `smartportx-yard/.../domain/EquipmentStatus.java` | `AVAILABLE`, `ASSIGNED`, `OUT_OF_SERVICE` |
| `OptimizationStrategy` | `smartportx-yard/.../domain/OptimizationStrategy.java` | `FIFO`, `WEIGHT_CLUSTER`, `DEPARTURE_CLUSTER`, `BALANCED` |
| `OptimizationStatus` | `smartportx-yard/.../domain/OptimizationStatus.java` | `COMPLETED`, `FAILED`, `IN_PROGRESS` |
| Tier status (derived) | `YardController` / `LayoutResponse.Tier.from` | `OCCUPIED` \| `EMPTY` |
| `NotificationCategory` | `smartportx-notification/.../domain/NotificationCategory.java` | `YARD_UPDATE`, `GATE_EVENT`, `VESSEL_UPDATE`, `CONTAINER_EVENT`, `APPOINTMENT`, `ALERT`, `CUSTOMS`, `IOT` |
| `NotificationSeverity` | `smartportx-notification/.../domain/NotificationSeverity.java` | `INFO`, `WARNING`, `ERROR`, `CRITICAL` |

Severity derivation (FACT, `NotificationSeverity.from` / `defaultFrom`): `CRITICAL`→`CRITICAL`; `HIGH`, `WARNING`, `OUT_OF_SERVICE`, `HELD`, `UNDER_REVIEW`, `EXAMINATION`→`WARNING`; `ERROR`→`ERROR`; everything else→`INFO` (`defaultFrom` only maps the five status strings→`WARNING`, else `INFO`).

## 3. REST endpoints — `smartportx-yard`, base `/api/v1/yard` (`YardController.java`)

| Method & Path | Auth (`@PreAuthorize`) | Request | Response |
|---|---|---|---|
| `GET /layout?includeEmpty=true&blockFilter=A,B` | authenticated | `includeEmpty` (default `true`), `blockFilter` optional list of block codes | `LayoutResponse` (§3.1) |
| `GET /blocks` | authenticated | — | `BlocksResponse` (§3.2) |
| `GET /blocks/{blockId}` | authenticated | — | bare `LayoutResponse.Block` (§3.1) — no envelope |
| `PUT /blocks/{blockId}` | `ADMIN`,`YARD_MANAGER`,`TERMINAL_OP` | `UpdateBlockRequest` (§3.2) | `BlockSummaryResponse` (§3.2) |
| `GET /blocks/{blockId}/bays` | authenticated | — | `BayListResponse` (§3.2) |
| `GET /blocks/{blockId}/bays/{bayId}/rows/{rowId}/tiers/{tierId}` | authenticated | — | `TierDetailResponse` (§3.2) |
| `PUT /blocks/{blockId}/bays/{bayId}/rows/{rowId}/tiers/{tierId}/container` | `ADMIN`,`YARD_MANAGER`,`TERMINAL_OP` | `AssignContainerRequest` = `{ "containerId": uuid, "position": pos\|null }` (position ignored) | `AssignResponse` = `{ tierId, position, status:"OCCUPIED", containerId, containerNumber, containerType }` |
| `DELETE /blocks/{blockId}/bays/{bayId}/rows/{rowId}/tiers/{tierId}/container` | `ADMIN`,`YARD_MANAGER` | — | `RemoveContainerResponse` = `{ removedContainerId, position, removedAt, movedTo }` |

**FACT** — There is **no move endpoint**; moving = `DELETE` source tier + `PUT` destination tier (`YardAssignmentService.move` is service-internal only).

**FACT** — `YardController` endpoint body shapes from `YardController.java` (178 lines, fully read).

### 3.1 LayoutResponse (`web/dto/LayoutResponse.java`)

```json
{
  "yardId": "yard-001", "yardName": "Main Terminal Yard",   // hardcoded by from()
  "totalBlocks": 1, "totalSlots": 0, "occupiedSlots": 0,
  "utilizationRate": 0.0, "lastUpdated": "<iso>",
  "blocks": [{
    "blockId": "<uuid>", "blockCode": "A", "name": "...",
    "type": "EXPORT" | null,
    "totalBays": 0, "totalRows": 0, "totalTiers": 0,
    "totalSlots": 0, "occupiedSlots": 0, "utilizationRate": 0.0,
    "bays": [{
      "bayId": "<uuid>", "bayNumber": 1,
      "rows": [{
        "rowId": "<uuid>", "rowNumber": 1,
        "tiers": [{
          "tierId": "<uuid>", "tierNumber": 1,
          "position": "A-B01-R01-T01", "status": "EMPTY" | "OCCUPIED",
          "containerId": "<uuid>|null", "containerNumber": null,   // always null
          "containerType": "...|null",
          "stackWeight": 123 | null,   // present ONLY when occupied
          "maxWeight": 20000.0 | null, // block maxStackWeight
          "dangerousGoods": null, "estimatedDeparture": null        // always null
        }]
      }]
    }]
  }]
}
```

Layers (`LayoutResponse.from` → `Block.from` → `Bay.from` → `Row.from` → `Tier.from`):
- `totalSlots`/`occupiedSlots` on Block come from `BlockSnapshot` computed in `YardQueryService.buildBlock`; block `totalSlots = totalBays × totalRows × totalTiers` (`YardBlock.totalSlots()`).
- **FACT** — layout `Tier.stackWeight` equals the *current container's weight rounded to int* (`YardQueryService.columnWeight`, a single-tier value — **not** a true column sum). Empty → `null`.
- **FACT** — when `includeEmpty=false`, `YardQueryService.buildBlock` mutates snapshots: removes empty tiers, then removes rows whose tiers are all gone.

### 3.2 Blocks / bays / slot DTOs (`web/dto/BlocksResponse.java`)

`GET /blocks` response — **`pagination` is always `null`** because controller uses `BlocksResponse.of(data)`:

```json
{ "data": [ {
    "blockId": "<uuid>", "blockCode": "A", "name": "...", "type": "EXPORT" | null,
    "totalSlots": 0, "occupiedSlots": 0, "utilizationRate": 0.0,
    "averageDwellDays": null,     // hardcoded null in controller
    "dangerousGoodsAllowed": true,
    "refrigeratedSlots": 0        // hardcoded 0 in controller
  } ],
  "pagination": null }
```

`GET /blocks/{blockId}/bays`:
```json
{ "data": [ {
    "bayId": "<uuid>", "bayNumber": 1,
    "totalRows": 0, "totalTiers": 0, "totalSlots": 0, "occupiedSlots": 0,
    "utilizationRate": 0.0, "maxStackWeight": 20000.0 | null
  } ] }
```

`GET .../tiers/{tierId}` (`TierDetailResponse`):
```json
{ "tierId": "<uuid>", "position": "A-B01-R01-T01", "status": "EMPTY" | "OCCUPIED",
  "container": null | {
    "id": "<uuid>", "number": "MSKU...",         // NOTE: wire key "number" (from containerNumber)
    "type": "...", "owner": null, "weight": 0.0 | null,
    "status": "...", "dangerousGoods": null,
    "receivedAt": "<iso>|null", "estimatedDeparture": "<iso>|null"
  },
  "stackWeight": null, "maxWeight": null,        // always null from controller
  "stackCount": 1,                               // row.getTierCount()
  "lastModified": "<iso>" }                      // Instant.now() at request time
```

`PutBlock` request (`UpdateBlockRequest`): `{ "name": "...", "type": "EXPORT", "dangerousGoodsAllowed": true, "maxStackWeight": 20000.0 }` — all optional; unknown `type` → error `invalid_block_type` (422).

### 3.3 Metrics (`web/dto/MetricsResponse.java`, `YardMetricsController.java`)

`GET /metrics?period=24h` (period echoed; default `24h`):
```json
{ "yardId": "yard-001", "period": "24h", "generatedAt": "<iso>",
  "totalSlots": 0, "occupiedSlots": 0, "availableSlots": 0, "utilizationRate": 0.0,
  "utilizationTrend": [{ "timestamp": "<iso>", "rate": 0.0 }],
  "averageDwellTimeDays": null,
  "dwellTimeDistribution": null,     // always null via MetricsResponse.from
  "reshufflingRate": 0.0,
  "totalMoves24h": null, "averageMovesPerHour": null,
  "containersReceived24h": null, "containersDispatched24h": null,
  "dangerousGoodsCount": 0, "refrigeratedCount": 0, "emptyContainerCount": 0 }
```
(`dwellTimeDistribution` DTO shape if ever populated: `{ range, count, percentage }`.)

`GET /metrics/blocks` → `{ "data": [...] }`:
```json
{ "blockId": "<uuid>", "blockCode": "A", "blockType": "EXPORT", "totalSlots": 0,
  "occupiedSlots": 0, "utilizationRate": 0.0, "averageDwellDays": null,
  "reshufflingRate": 0.0, "dangerousGoodsSlots": 0, "refrigeratedSlots": 0, "emptySlots": 0 }
```

### 3.4 Equipment (`web/dto/EquipmentResponse.java`, `YardEquipmentController.java`)

`GET /equipment?type=RTG&status=AVAILABLE`, `GET /equipment/{id}`, `PUT /equipment/{id}`
- `UpdateRequest` body: `{ "status": "AVAILABLE", "assignedBlockId": "<uuid>|null" }`
- Response: `{ "equipmentId": uuid, "code": "RTG-01", "equipmentType": "RTG", "assignedBlockId": uuid|null, "status": "AVAILABLE", "movesTotal": 0, "createdAt": "<iso>", "updatedAt": "<iso>" }`
- List form: `{ "data": [...] }`.

`GET /equipment/{id}/moves` → `{ "equipmentId": uuid, "code": "RTG-01", "movesTotal": 0, "lastUpdated": "<iso>" }` (`lastUpdated` = entity `updatedAt`).

### 3.5 Optimization (`web/dto/OptimizationResponse.java`, `YardOptimizationController.java`)

`POST /optimize` — body (`OptimizationRequest`): `{ "blockId": uuid, "strategy": "WEIGHT_CLUSTER", "dryRun": true, "constraints": { "maxResufflesPerMove": 5, "preserveReeferPositions": true, "avoidDangerousMixing": true } }`. `blockId` required (else `validation_failed` 400); unknown strategy → `invalid_strategy` 422; strategy/constraints optional.

Response:
```json
{ "optimizationId": "<uuid>", "blockId": "<uuid>", "strategy": "WEIGHT_CLUSTER", "dryRun": true,
  "analysis": { "totalContainers": 0, "currentReshuffles": 0, "projectedReshuffles": 0,
                "estimatedReshufflingReduction": 0.0, "containersAffected": 0, "estimatedExecutionTimeMs": 0 },
  "recommendations": [{ "sequence": 1, "containerId": uuid, "containerNumber": "MSKU...",
       "currentPosition": "A-B01-R01-T01", "recommendedPosition": "A-B02-R04-T02",
       "reason": "...", "moveType": "...", "priority": "..." }],
  "status": "COMPLETED" | "FAILED" | "IN_PROGRESS",
  "executedAt": "<iso>", "completedAt": "<iso>",
  "movesCompleted": 0, "movesFailed": 0, "actualReshufflingReduction": 0.0 }
```

`GET /optimize/history?cursor=&limit=25&blockId=&status=` — record name `HistoriesResponse`:
```json
{ "data": [ {
    "optimizationId": "<uuid>", "blockId": "<uuid>", "blockCode": "<nullable>",
    "strategy": "FIFO", "status": "COMPLETED", "dryRun": false,
    "containersAffected": 0, "reshufflingReduction": 0.0,
    "triggeredBy": "...", "executedAt": "<iso>", "completedAt": "<iso>"
  } ],
  "pagination": { "nextCursor": "next" | null, "hasMore": false, "totalCount": 0, "limit": 25 } }
```
**FACT** — `cursor` is accepted but unused; paging is limit-based with `hasMore`; `nextCursor` is literally the string `"next"` when more pages exist (`YardOptimizationController.history`). `limit` clamped 1–100; unknown `status` → 422 `invalid_status`.

## 4. Error envelope (RFC 9457) — `smartportx-common/.../error/`

`GlobalExceptionHandler` + `ApiProblems` (both fully read):

```json
{ "type": "urn:smartportx:problems:<code>", "title": "<HTTP reason phrase>", "status": 409,
  "detail": "...",
  "code": "conflict",
  "fieldErrors": { "field": "message" },      // present on validation_failed only
  "errorId": "<uuid>",                         // present on internal_error (500) only
  "correlationId": "<uuid>",                   // when a correlation header is present
  "instance": "/api/v1/yard/..." }
```

Standard mappings (FACT):
- `validation_failed` (400) — bean/constraint violations, per-field `fieldErrors`
- `malformed_request` (400) — unreadable body / type mismatch / missing param / `IllegalArgumentException` (generic message, no field echoes)
- `not_found` (404) — `NotFoundException` + `NoResourceFoundException`
- `conflict` (409) — `ConflictException` + `DataIntegrityViolationException`
- `internal_error` (500) — generic detail + `errorId`, exception never echoed
- Security: `unauthorized` (401) / `access_denied` (403) via `JsonProblemHandler`

Yard-specific codes raised in code (FACT): `invalid_block_type` (422), `invalid_strategy` (422), `invalid_status` (422), `invalid_container_status` (422), `dangerous_goods_not_allowed` (422), `reefer_requires_refrigerated_block` (422), `weight_limit_exceeded` (422), `stacked_containers_above` (422). Assign to occupied slot → `ConflictException` (409). Not-found chain in `YardQueryService.tier/resolvePosition` → `NotFoundException` (404).

## 5. STOMP topics — `smartportx-notification`

**FACT** — `WebSocketConfig`: STOMP endpoints `/ws` and `/ws/notifications` (SockJS, `setAllowedOriginPatterns("*")`); broker prefix `/topic`; app prefix `/app`; inbound channel intercepts requests (JWT auth). Serves ALL notifications (container, yard, gate, vessel, customs).

### 5.1 Frame payload — `NotificationPayload`

```json
{ "topic": "/topic/yard-status", "notificationId": "ntf-<uuid>",
  "category": "YARD_UPDATE", "severity": "INFO",
  "title": "Yard Position Assigned",
  "message": "Container <id> assigned to A-B01-R01-T02",
  "timestamp": "<iso>", "actionUrl": null, "data": { <raw domain-event payload> } }
```

**FACT** — `notificationId` = `"ntf-" + UUID.randomUUID()`; `timestamp` = `clock.now()` at broadcast.

### 5.2 Topic authorization — `TopicAuthorizationService`

Rules are exact segment matches; `*` matches exactly one non-empty segment; relative order = first match wins (longest-matching rule must therefore be listed appropriately); **fail-closed**: no matching rule or no authorities → denied; `ROLE_ADMIN` always allowed (FACT, fully read):

| Destination | Required authority |
|---|---|
| `/topic/yard-status` | `ROLE_TERMINAL_OP` |
| `/topic/yard-zone/*` | `ROLE_YARD_MANAGER` |
| `/topic/gate-status` | `ROLE_GATE_OPERATOR` |
| `/topic/gate-lane/*` | `ROLE_GATE_OPERATOR` |
| `/topic/vessel-status` | `ROLE_TERMINAL_OP` |
| `/topic/vessel/*` | `ROLE_TERMINAL_OP` |
| `/topic/containers/*` | any authenticated |
| `/topic/appointments/*` | `ROLE_GATE_OPERATOR` |
| `/topic/customs/*` | `ROLE_TERMINAL_OP` |
| `/topic/dashboard/summary` | any authenticated |
| `/topic/alerts` | any authenticated |
| `/topic/alerts/*` | any authenticated |

### 5.3 Event → topic mapping — `NotificationEventRouter.route` (FULLY read)

| Event type (routing key) | Pushed topic(s) | Category / severity | `data` keys read |
|---|---|---|---|
| `container.ContainerRegistered` | `/topic/containers/{containerNumber}` | CONTAINER_EVENT / INFO | `containerNumber` |
| `container.ContainerStatusChanged` | `/topic/containers/{containerNumber}` | CONTAINER_EVENT / INFO | `containerNumber`, `newStatus` |
| `container.ContainerMoved` | `/topic/containers/{containerNumber}` | CONTAINER_EVENT / INFO | `containerNumber`, `from`, `to` |
| `container.ContainerRemoved` | `/topic/containers/{containerNumber}` | CONTAINER_EVENT / INFO | `containerNumber` |
| `yard.YardPositionAssigned` | `/topic/yard-status`, `/topic/yard-zone/{blockCode}` | YARD_UPDATE / INFO | `containerId`, `position`, `blockCode` |
| `yard.YardPositionFreed` | `/topic/yard-status` | YARD_UPDATE / INFO | `position` |
| `yard.YardOptimizationRun` | `/topic/yard-status`, `/topic/dashboard/summary` | YARD_UPDATE / INFO | `strategy`, `reshufflingReduction` |
| `yard.EquipmentStatusChanged` | `/topic/yard-status` | YARD_UPDATE / `defaultFrom(newStatus)` | `code`, `newStatus` |
| `gate.AppointmentBooked/Confirmed/Cancelled/Rescheduled` | `/topic/appointments/{appointmentId}` | APPOINTMENT / INFO | `appointmentId`, `confirmationNumber`, `containerNumber` |
| `gate.GateCheckIn` | `/topic/gate-lane/{gateLane}`, `/topic/gate-status` | GATE_EVENT / INFO | `containerNumber`, `gateLane` |
| `gate.GateCheckOut` | `/topic/gate-lane/{gateLane}`, `/topic/gate-status` | GATE_EVENT / INFO | `containerNumber`, `gateLane` |
| `gate.GateQueueUpdated` | `/topic/gate-status`, `/topic/dashboard/summary` | GATE_EVENT / INFO | `waitingCount` |
| `vessel.VesselArrived` | `/topic/vessel/{vesselId}`, `/topic/vessel-status` | VESSEL_UPDATE / INFO | `vesselId`, `vesselName`, `berthNumber` |
| `vessel.VesselDeparted` | `/topic/vessel/{vesselId}`, `/topic/vessel-status` | VESSEL_UPDATE / INFO | `vesselId`, `vesselName` |
| `vessel.DischargePlanCreated` | `/topic/vessel-status` | VESSEL_UPDATE / INFO | `planId`, `vesselId` |
| `vessel.DischargeProgressUpdated` | `/topic/vessel-status` | VESSEL_UPDATE / INFO | `dischargedCount`, `totalContainers` |
| `integration.EdifactParsed` | `/topic/vessel-status` | VESSEL_UPDATE / INFO | `messageType` |
| `integration.DischargeOrderReceived` | `/topic/vessel-status` | VESSEL_UPDATE / INFO | `routeCode` |
| `integration.ArrivalAnnounced` | `/topic/containers/{containerNumber}`, `/topic/vessel-status` | CONTAINER_EVENT + VESSEL_UPDATE / INFO | `containerNumber` |
| `integration.IotAlertTriggered` | `/topic/containers/{containerNumber}` | IOT / `from(alertLevel)` | `containerNumber`, `sensorType`, `message`, `alertLevel` |
| `integration.CustomsStatusChanged` | `/topic/customs/{containerNumber}` | CUSTOMS / `from(newStatus)` | `containerNumber`, `newStatus` |
| unknown | none (ignored) | — | — |

Custom message templates use key `humanize`, substituting `"-"` for blank values. Multiple pushes on one event are all broadcast; the *worst* severity across them is computed; if `worst >= WARNING` the event is also handed to `AlertEngine.routeAlert`.

### 5.4 Alerts — `AlertEngine` + `NotificationBroadcaster` (FULLY read)

- Published topics (`NotificationBroadcaster`): `/topic/alerts` **and** `/topic/alerts/{severity-lowercase}` (e.g. `/topic/alerts/critical`).
- Category is always `ALERT`; history recorded for audit.
- **Deduplication** (`AlertEngine.dedupeKey`): key = `eventType + "|" + selector`, selector = the first non-blank of `containerNumber`, `vesselId`, `blockCode`, `gateLane`, `appointmentId`, `code`; window `${smartportx.notification.alert.dedupe-window-seconds}` (default 300 s).
- `routeAlert` no-ops unless `severity >= WARNING`.
- Alert titles by source event: `integration.IotAlertTriggered` → "IoT Sensor Alert"; `yard.EquipmentStatusChanged` → "Equipment Out of Service"; `integration.CustomsStatusChanged` → "Customs Hold"; default "Operations Alert".

## 6. Cross-cutting notes for the dashboard client

1. **Per-container topics use the container number** (`MSKU...`), not the UUID — client must subscribe with the container number as the last segment.
2. **`GET /blocks/{blockId}` is unwrapped** (bare block object); `GET /blocks` and `/blocks/{id}/bays` have a `data` wrapper; `/layout` has none.
3. **Unknown event types are silently ignored** (fail-open by omission, logged upstream in consumer).
4. Coordinate keys read by the router are only the ones in the table above; other domain-event payload fields still travel verbatim inside `data` (full `DomainEvent` payload is passed through).
5. There is no `stackCount` on the layout `Tier` (only on `TierDetailResponse`); layout `Tier.stackWeight` is the single container's rounded weight when occupied, else `null`.

## 7. Source index

- `smartportx-yard/src/main/java/com/smartportx/yard/web/YardController.java`
- `smartportx-yard/src/main/java/com/smartportx/yard/web/YardMetricsController.java`
- `smartportx-yard/src/main/java/com/smartportx/yard/web/YardEquipmentController.java`
- `smartportx-yard/src/main/java/com/smartportx/yard/web/YardOptimizationController.java`
- `smartportx-yard/src/main/java/com/smartportx/yard/web/dto/{LayoutResponse,BlocksResponse,MetricsResponse,EquipmentResponse,OptimizationResponse,PaginationResponse}.java`
- `smartportx-yard/src/main/java/com/smartportx/yard/service/{YardQueryService,YardAssignmentService,YardMetricsService,YardOptimizationService,ContainerCatalog}.java`
- `smartportx-yard/src/main/java/com/smartportx/yard/domain/YardPosition.java` (formatting contract)
- `smartportx-common/src/main/java/com/smartportx/common/error/{GlobalExceptionHandler,ApiException,ApiProblems,ApiError}.java`
- `smartportx-notification/src/main/java/com/smartportx/notification/config/WebSocketConfig.java`
- `smartportx-notification/src/main/java/com/smartportx/notification/stomp/TopicAuthorizationService.java`
- `smartportx-notification/src/main/java/com/smartportx/notification/service/{NotificationEventRouter,NotificationBroadcaster,AlertEngine,NotificationPush}.java`
- `smartportx-notification/src/main/java/com/smartportx/notification/web/dto/NotificationPayload.java`
- `smartportx-notification/src/main/java/com/smartportx/notification/domain/{NotificationCategory,NotificationSeverity}.java`