# M11 — Dashboard (React 18 + TS) — Implementation Plan

Phase 4 · increment M11 of `PHASE-4-IMPLEMENTATION-PLAN.md` (§ M11, lines 267–281).
Status: **M11.1–M11.7 done** · M11.7–M11.8 pending · M11.9 staged · Owner: M11 dev · Gate: M11 DoD + `npm run lint` + `npm run typecheck` +
`npm test` green; live-stack smoke recorded honestly (see §Verification).

## Scope

| Task | Area | Summary |
|---|---|---|
| M11.1 | App shell | routing, AuthContext (access/refresh persist + silent refresh), axios interceptor (401→refresh→retry once→logout), env config, Vite dev proxy | **DONE** (33 tests) |
| M11.2 | Login | real `POST /api/v1/auth/login` (via gateway); role-aware default landing; logout (`POST /api/v1/auth/logout`) | **DONE** (7 LoginScreen tests) |
| M11.3 | Dashboard | STOMP hook to `/topic/dashboard/summary` + `/topic/alerts`; live KPI tiles (gate waiting, yard occupancy, container movements); loading/empty/error states | **DONE** (56 total tests green) |
| M11.4 | Containers | search/list (real API), detail + tracking timeline (events/status-history/movements), state badge | **DONE** (75 total tests green) |
| M11.5 | Yard | block/bay view (real data), optimization panel ("why" reason), move request | **DONE** (YardScreen 10 tests + yardApi 9 tests; suite 94/94 across 12 files; lint/typecheck clean) |
| M11.6 | Gate | appointment booking (real API + preview/confirm/cancel), live queue via `/topic/gate-status` + `/topic/gate-lane/{laneId}`, check-in/out (per role) | **DONE** (GateScreen 19 tests + gateApi 16 tests; suite 129/129; lint clean, typecheck clean) |
| M11.7 | Vessel | schedules, discharge progress live via `/topic/vessel/{vesselId}` | **DONE** | **DONE** | **DONE**
| M11.8 | Alerts + Admin | alerts feed (+ severity), admin user mgmt + notification preferences; READONLY sees read-only surfaces |
| M11.9 | a11y/state audit | building-accessible-interfaces pass; Playwright smoke E2E (login→dashboard) staged pending live stack |

## Source-of-truth decisions (reconciliations)

- **No fake data.** Plan §M11 requires every screen to hit real backend or real WS topics. Unit/
  component tests may mock HTTP/WS, but fixtures must mirror the verified contracts below; a screen
  is only "done" when it renders real payloads from the running stack (or the stack blocker is
  recorded as evidence, see §Verification).
- **Gateway is the single origin.** Browser talks only to `http://<host>:8080` — `/api/**` REST and
  `/ws` (STOMP) both proxied by the gateway (M10 route table: container/yard/gate/vessel/identity/
  notification REST + `lb:ws://smartportx-notification` for `/ws/**`). No service is reached
  directly. Vite dev proxy mirrors this (`/api` → `http://localhost:8080`, `/ws` → `ws://localhost:8080`).
- **Endpoint surface (FACT, verified in controllers):** identity `AuthController` (`/api/v1/auth/login|refresh|logout`,
  `PUT /password`, `GET /jwks`), `UserAdminController` (`/api/v1/users` GET/POST, `/{id}` GET/PUT,
  `/{id}/disable` PATCH, `/{id}` DELETE) · container `ContainerController` (`/api/v1/containers` GET/POST,
  `/search` POST, `/number/{containerNumber}` GET, `/{id}` GET/PUT/DELETE, `/{id}/movements` GET/POST,
  `/{id}/events`, `/{id}/status-history`, `/{id}/status` PUT (state change, not consumed by
  dashboard UI this increment) · yard `YardController`
  (`/api/v1/yard/layout`, `/blocks`, `/blocks/{id}`, `/blocks/{id}/bays`, slot container PUT/DELETE),
  `YardMetricsController` (`/api/v1/yard/metrics`, `/metrics/blocks`), `YardEquipmentController`
  (`/api/v1/yard/equipment`), `YardOptimizationController` (`/api/v1/yard/optimize` POST,
  `/optimize/history` GET) · gate `AppointmentController` (`/api/v1/appointments` GET/POST,
  `/{id}` GET/PUT, `/preview`, `/{id}/confirm`, `/{id}/cancel`), `QueueController`
  (`/api/v1/gate/queue` GET), `CheckInOutController` (`/api/v1/gate/check-in|check-out`),
  `QuotaController` (`/api/v1/quotas`) · vessel `VesselController` (`/api/v1/vessels` CRUD,
  `/vessels/{id}/schedules`, `/schedules/in-port`, `/vessels/{id}/discharge-plans`,
  `/discharge-plans/{planId}`, `/discharge-plans/{planId}/progress` PATCH) · custom status
  `IntegrationController` `/api/v1/integration/customs/status/{containerNumber}` (shown on container
  detail) · notification `NotificationPreferenceController` (`/api/v1/notifications/preferences`
  GET/PUT).
- **WS surface (FACT, verified in `WebSocketConfig` + doc §3.4):** STOMP endpoint `/ws` (SockJS,
  `withOriginPatterns("*")`), simple broker `/topic`, app prefix `/app`. Authorized topics:
  `/topic/dashboard/summary`, `/topic/alerts`, `/topic/alerts/{severity}`, `/topic/yard-status`,
  `/topic/yard-zone/{zoneId}`, `/topic/gate-status`, `/topic/gate-lane/{laneId}`,
  `/topic/vessel-status`, `/topic/vessel/{vesselId}`, `/topic/containers/{containerNumber}`,
  `/topic/appointments/{appointmentId}`, `/topic/customs/{containerNumber}`.
  `JwtStompChannelInterceptor` requires the JWT on the STOMP CONNECT frame (gateway exempts `/ws/**`
  from edge JWT — the handshake authorizes). Dashboard client must attach `Authorization` on CONNECT.
- **Summary-topic producer (RESOLVED — decision (b), recorded in E-033):** read
  `NotificationEventRouter` + `TopicAuthorizationService`. `/topic/dashboard/summary` is NOT an
  aggregated KPI object — it is a fan-out of consumed `gate.GateQueueUpdated` +
  `yard.YardOptimizationRun` DomainEvents, payload = raw event body (e.g. `waitingCount`;
  `strategy` + `reshufflingReduction`). All topics carry `web.dto.NotificationPayload`:
  `{topic, notificationId ("ntf-<uuid>"), category (enum UPPERCASE: YARD_UPDATE|GATE_EVENT|VESSEL_UPDATE|CONTAINER_EVENT|APPOINTMENT|ALERT|CUSTOMS|IOT), severity (INFO|WARNING|ERROR|CRITICAL), title, message, timestamp (ISO-8601), actionUrl, data (free JSON)}`. Alerts go to `/topic/alerts` AND `/topic/alerts/{severity.toLowerCase()}` (deduped 300 s window in `AlertEngine`). **Dashboard KPI therefore aggregates client-side:** REST snapshot (`/api/v1/gate/queue`, `/api/v1/yard/metrics`, `/metrics/blocks`, container movement/status counts) + WS deltas from `/topic/alerts|gate-status|yard-status`.
- **JWT claims (FACT, verified):** `sub`=user UUID, `roles`=`List.of(RoleName.name())`, `permissions`=role permission strings (`RolePermissions`), `username`, `email`, `iat`, `exp`, `iss`, `jti`. RoleName enum: `ADMIN, TERMINAL_OP, YARD_MANAGER, GATE_OPERATOR, READONLY` (identity `domain/RoleName.java`). `UserResponse.role` = same string. Frontend decodes `roles`/`permissions` for nav/buttons only.
- **Confirmations for dashboard rest calls (FACT, verified in DTOs/controllers):**
  - `GET /api/v1/gate/queue` → `QueueView{waitingTrucks, pickupCount, dropoffCount, averageWaitMinutes, byGateLane:[{lane,status,currentQueue,averageWait,estimatedWaitMinutes}], upcomingAppointments:[{confirmationNumber,type,truckLicensePlate,containerNumber,preferredTimeSlot}], operator, instructions}`.
  - `GET /api/v1/yard/metrics` → `MetricsResponse{yardId,period,generatedAt,totalSlots,occupiedSlots,availableSlots,utilizationRate,utilizationTrend:[{timestamp,rate}],averageDwellTimeDays,dwellTimeDistribution,reshufflingRate,totalMoves24h,averageMovesPerHour,containersReceived24h,containersDispatched24h,dangerousGoodsCount,refrigeratedCount,emptyContainerCount}`. `GET /api/v1/metrics/blocks` → `BlockMetricsList.data:[BlockMetricsData]`.
  - Containers paged shape is uniform `{pagination:{nextCursor,hasMore,totalCount,limit}, data:[...]}` (`ContainersPage|MovementsPage|EventsPage`). ContainerStatus: `REGISTERED, AT_GATE, IN_YARD, LOADED, DISCHARGED, AT_GATE_OUT, DEPARTED, HELD`; ContainerType wire values `20DC,40DC,40HC,45HC,20RF,40RF,20TK,40TK`.
  - Vessel lists: `ListResponse{items:[...], nextCursor}`. Yard layout: `LayoutResponse` → `blocks[]->bays[]->rows[]->tiers[{...status:"OCCUPIED"|"EMPTY", containerNumber,...}]`. Gate appointment wire ids prefixed `apt-<uuid>`; check-in/out `txn-<uuid>`.
- **Token storage (DESIGN PROPOSAL, flagged):** access token kept in memory (not localStorage —
  XSS), refresh token also in memory with 15–30 min silent-refresh scheduler; full page reload →
  re-login. Cookie/httpOnly storage needs identity+gateway changes (out of scope, noted in M11.1).
  Frontend role checks are UI-only — **backend still enforces authz** (plan M11.8; READONLY surfaces
  hidden in nav, not trusted).
- **Live-stack gap (FACT):** `dev-up.ps1` (referenced by plan M13.1) does not exist yet, and RabbitMQ
  is unavailable locally (R-009; mitigation WSL2 docker OR choco). WS deltas need broker-fed events.
  M11 verification tiers in §Verification.

## Frontend design

### M11.1 App shell
- Add deps: `axios` (typed), `@stomp/stompjs` + `sockjs-client` (+ `@types/sockjs-client`); stays on
  React 18 + TS strict (already: `tsc -b`, vitest + RTL + jsdom configured).
- `vite.config.ts`: server.proxy `/api`→8080, `/ws`→8080 (ws:true). Env via `VITE_API_BASE` (default
  `/api`).
- `AuthContext`: in-memory token store + decode JWT claims (`sub`, `roles`) for UI; schedules silent
  refresh before expiry (`POST /api/v1/auth/refresh`) with single-flight; auto-logout on failure.
- `http.ts`: axios instance, attach `Authorization: Bearer`, response interceptor — on 401
  refresh→retry original once→else logout→`/login`; RFC 9457 problem+json surfaced to error states.
- Router + `RequireAuth`/`ByRole` guards; layout nav filtered by role.

### M11.2 Login
- Form → `POST /api/v1/auth/login` (gateway public). On success store tokens, decode role, redirect
  to role default (ADMIN→admin, TERMINAL_OP→dashboard, YARD_MANAGER→yard, GATE_OPERATOR→gate,
  READONLY→dashboard read-only). Logout button → `POST /api/v1/auth/logout` + clear state.

### M11.3 Dashboard — **DONE** (56 tests/8 files green, lint+typecheck clean)
- `useStomp` hook: SockJS→STOMP to `/ws` with `Authorization` on CONNECT; reconnect w/ backoff;
  subscribe `/topic/dashboard/summary` + `/topic/alerts`.
- KPI tiles: gate waiting (REST snapshot `/api/v1/gate/queue` length + WS `gate-status` delta),
  yard occupancy (`/api/v1/yard/metrics` + `/metrics/blocks`), container movements (event/status
  count deltas). Loading/empty/error/empty-state components reused app-wide.
- **OOM fixed:** `useStomp` effect previously re-ran on every render because test `Harness` passed a
  fresh `topics` array identity AND cleanup called `setMessages({})` (new `{}` ref each time) → infinite
  re-render loop → unbounded FakeStompClient creation → 4 GB heap exhaust. Fix: stabilize topics via
  `topicsRef` + `topicsKey` (join `\0`) in the dependency array; removed `setMessages({})` from cleanup.
  Three tests updated to fire STOMP callbacks inside `act()` (React 18 auto-batching).

### M11.4 Containers — **DONE** (75 tests / 10 files green, lint+typecheck clean)
- Search (POST `/search`) + list (GET `/containers`); detail (GET `/{id}`) with tracking timeline
  (`GET /{id}/events`, `/status-history`, `/movements`), customs status widget
  (`/integration/customs/status/{string}`), state badge per verified state machine; live-updates via
  `/topic/containers/{containerNumber}` when a container is open.
- TDD tests cover: paginated list rendering, search input wiring, detail + timeline tab switching,
  customs fallback text, STOMP live-update chip, and role-gated access (TERMINAL_OP/READONLY vs
  YARD_MANAGER redirect). 4 originally-failing tests (ambiguous View button role) fixed by scoping
  via `within(container-row-{num})`.

### M11.5 Yard
- Layout → blocks → bays/slot view (`GET /api/v1/yard/layout`, `/blocks`, `/blocks/{id}/bays`);
  slot-level copy of real occupancy. Optimization panel (POST `/optimize` → decision + "why"
  reason, `GET /optimize/history`), run/move per role (YARD_MANAGER/ADMIN/TERMINAL_OP); move request
  = slot container PUT with target slot payload.
- **Verification:** `YardScreen.test.tsx` 10/10 green (render, loading/error+retry, empty, READONLY
  read-only + no live status, move pick→drop→confirm→reload, cancel, dry-run→execute, STOMP live
  refresh, block switch). `yardApi.test.ts` 9/9. Full suite 94/94 green across 12 files — wiring
  `/yard` surfaced a regression in `LoginScreen.test.tsx` (YARD_MANAGER landing now reaches the real
  `YardScreen`: heading changed to "Yard Operations", unmocked `yardApi`), fixed by mocking `yardApi`
  + aligning the expectation. `npm run typecheck` clean; `npm run lint` 0 errors + 1 pre-existing
  react-refresh warning (`AuthContext.tsx:187:17`).
- **Known notes:** yardApi/history `params` default `{}` — safe, not a real bug. JSX text nodes use
  real Unicode (no literal `\uXXXX` escapes). Gate receipts: saw while wiring not repeated
  semantically — real stack smoke deferred to M11.9 (see §Verification tier 2 blocker).

### M11.6 Gate
- Appointments list (GET `/appointments`), booking form (POST + `/preview` for slot/time proposal +
  `/confirm`, `/cancel`), role-gated. Live queue: `/topic/gate-status` + `/topic/gate-lane/{laneId}`
  + REST `/api/v1/gate/queue`. Check-in/out buttons (POST `/gate/check-in|check-out`) shown to
  GATE_OPERATOR/TERMINAL_OP/ADMIN only.
  **DONE:** 35 tests (GateScreen 19 + gateApi 16); suite 129/129, `tsc --noEmit` 0 errors, lint
  0 errors. Route `/gate` live in `App.tsx` + `AppLayout` nav; shared CSS only (no gate-specific
  additions needed). GateScreen test root-cause fixed: `renderGate` missing `expiresIn` → NaN
  `expiresAt` → silent-refresh fires at 0 ms → `resetToAnonymous`; now stable and 19/19.

### M11.7 Vessel — **DONE** (VesselScreen realigned to real DTOs; suite 151/151, tsc 0 err, lint 0)
- In-port schedules (`GET /schedules/in-port`), per-vessel schedules + discharge plans
  (`GET /vessels/{id}/schedules`, `/discharge-plans`, `/discharge-plans/{planId}`) with progress
  (PATCH only via UI actions where role allows); live progress via `/topic/vessel/{vesselId}`.
  Realigned VesselScreen + fixtures to authoritative DTOs in `src/api/types.ts` (VesselResponse,
  ScheduleResponse, DischargePlanResponse, DischargeContainerResponse, PlanProgress) — every
  container row, schedule row, plan chip, and progress block now maps only verified fields
  (`id`/`planId`/`scheduleId`, `voyageNumber`/`voyageReference`, `vesselPosition`, `portOfCall`/
  `nextPort`, `progress.dischargedCount`/`completionPercentage` + `stackedCount`, `vesselPosition`
  for container rows); removed stale `movesPerHour`/`containersDischarged` block; single TS2538
  (`NEXT_STATUS[c.status]` index) fixed via extracted `nextStatus` const. Fixtures rewritten to real
  DTO shapes (`TERMINAL_OP` REMOVED as unused → noUnusedLocals clean), `containerNumber`s realigned
  (`plan-plan-1`, `mark-MSKU…`, `sched-sched-1` testids verified), `nextCursor: null`,
  `/25 moves\/hr/` + "moves history" assertions dropped, `'0 discharged'|'1 discharged'` toggles
  with the status state machine. Full vitest suite re-run green (16 files / 151 tests),
  `tsc --noEmit` 0 errors, `npm run lint` 0 errors. Known harness act() warnings in
  Gate/GateScreen/Vessel tests are pre-existing test-only noise (state updates not act-wrapped),
  not product bugs; live-stack smoke remains staged behind R-007/R-009 (see §Verification).

### M11.8 Alerts + Admin
- Alerts feed: `/topic/alerts` + `/topic/alerts/{severity}` filter, with localStorage ack state.
- Admin (ADMIN only): user list/create/edit/disable (`/api/v1/users` …), role assignment; notification
  preferences self-service (GET/PUT `/api/v1/notifications/preferences`). READONLY: read-only nav.

### M11.9 a11y/state audit + E2E
- building-accessible-interfaces pass: focus management, contrast (tiles/list/buttons), aria on
  live regions + badges, reduced-motion respect, keyboard nav on yard/gate views.
- Playwright smoke (`login → dashboard KPI → alerts`) added as an npm script + `playwright.config.ts`
  hitting the real stack; **staged/`skip` until §Verification live layer is available**, with the
  run recorded in E-033 (not silently claimed green).

## Quality gates
- TDD per task (vitest + RTL, jsdom). Each screen ships: route guard test, loading/empty/error
  state tests, role-hiding test; hooks tested with mocked `http`/`ws` matching the contracts above.
- `npm run lint` + `npm run typecheck` + `npm test` all green before milestone close. If M11.3
  requires a backend change (summary producer), the affected service re-runs full `mvn -B clean
  verify` — gateway/notification/other modules shall not regress.

## Verification (honest tiers)
1. **Unit/component (always):** greens above, fixtures from verified contracts.
2. **Live stack (target):** native run of all 10 services (`mvn spring-boot:run`, local Postgres
   17.11 + 7 schemas, Eureka+gateway up), broker for event-fed WS. **In scope work item:** tiny
   `dev-up.ps1` native launcher so M11.9/M13.1 can run — but broker availability (R-009) and Docker
   (R-007) are workspace blockers, not resolvable inside this repo.
3. If the live layer cannot run here: every task still lands with its unit evidence AND a recorded
   "live-verify pending R-007/R-009" note in E-033 — no fake screens claimed against real data.

## Docs obligations (this increment)
- `ARCHITECTURE-OVERVIEW.md` §UI: replace scaffold note with the real dashboard architecture
  (screen map, auth flow, WS usage, role matrix).
- `COMMUNICATION-ARCHITECTURE.md` §3.4: add client conventions (CONNECT auth header, `/app` send
  prefix for any writes, SockJS vs raw WS guidance); correct summary-topic payload once verified.
- `API-CONTRACTS.md` §4.2: WS client contract (topics consumed per screen).
- `.env.example` fix (identity-only JWT comment cleanup, M10 leftover).
- `EVIDENCE.md` E-033 (dashboard); `IMPLEMENTATION-STATUS.md` rows: service row dashboard →
  IMPLEMENTED/TESTED w/ counts, FR-006 → VERIFIED (partial until M12 analytics NFR), FR-015 →
  VERIFIED (admin dashboard part), M11 milestone row.

## Open questions for ORCHESTRATOR/user before implementation
- Approve `/topic/dashboard/summary` decision path (extend router producer vs client-side
  aggregation) once M11.3 reads the router.
- Confirm token storage trade-off (in-memory + silent refresh, no cookie) is acceptable for the
  Phase-4 demo — cookie/httpOnly would require an M10.x follow-up on identity+gateway.
- Confirm scope: no register page (plan says "+ register if approved" — default OFF: seed users +
  admin-managed accounts).
