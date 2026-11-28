# SmartPortX Dashboard — Engineering & Design System

Authoritative design and architecture reference for the rebuild of `smartportx-dashboard`.
All visual and architectural claims are **DESIGN PROPOSAL** (per AGENTS.md classification).

---

## 1. Design direction — "industrial / precision / calm"

Operational terminal software for trained shift staff, not a consumer marketing site.
The aesthetic is **paper chart on a dark desk**: a warm paper canvas for work, a charcoal
shell for chrome, and a single restrained crimson signal color. Everything reads precise,
dense-but-organised, and unflashy.

| Antipattern (generic AI dashboard) | This system |
|---|---|
| Purple/teal gradient cards, glassmorphism | Flat panels, 1px hairline rules, no glow |
| Rounded-2xl pills, big soft shadows | 2px corners, flat or near-flat elevation |
| Neon status colors | Muted industrial pigments, always shape+icon+text |
| Decorative hero imagery | Dense data, uppercase kickers, mono figures |
| Centered one-column everything | Sticky rail + scannable multi-column grid |

**Signal rules (non-negotiable):**
1. **Crimson is signal, never decoration.** It marks: brand mark, primary/destructive
   actions, CRITICAL/ERROR states, live-attention, and required-field focus. Never used as
   plain text highlight without a shape/icon pair, and never as the *only* differentiator.
2. Color is never the sole indicator of state; every state carries a shape and (where
   meaningful) text. (WCAG 2.1 Success Criterion 1.4.1 AA.)
3. Figures are rendered in the mono face, tabular, right-aligned in tables.
4. Data changes pulse subtly once (150ms fade) only for live/WS-delivered updates; respect
   `prefers-reduced-motion` by skipping it.

---

## 2. Design tokens

Values are authoritative; implement as CSS custom properties in `src/styles/tokens.css`.

### 2.1 Color

**Canvas (work surface) — warm off-white "paper":**
| Token | Value | Use |
|---|---|---|
| `--color-canvas` | `#E9E5DA` | app background |
| `--color-surface` | `#F5F2EA` | cards/panels |
| `--color-surface-2` | `#ECE8DD` | raised panels, inputs, table header |
| `--color-ink` | `#1C1E1F` | primary text |
| `--color-ink-2` | `#4A4D4E` | secondary text |
| `--color-ink-3` | `#767A7B` | tertiary / meta |
| `--color-rule` | `#D0CBBE` | 1px hairline on paper |
| `--color-rule-strong` | `#B7B1A3` | emphasized rules |

**Shell (chrome) — charcoal desk:**
| Token | Value | Use |
|---|---|---|
| `--color-shell` | `#1A1D21` | sidebar / topbar / auth backdrop |
| `--color-shell-2` | `#24282D` | raised shell elements |
| `--color-shell-ink` | `#E6E2D8` | text on shell |
| `--color-shell-ink-2` | `#969B9F` | secondary on shell |
| `--color-shell-rule` | `rgba(230,226,216,0.12)` | hairlines on shell |

**Signal:**
| Token | Value | Use |
|---|---|---|
| `--color-accent` | `#A6342F` | crimson — brand, primary action, critical |
| `--color-accent-ink` | `#8C2B27` | crimson text on paper |
| `--color-accent-canvas` | `#F0D9D4` | crimson tint backgrounds |
| `--color-accent-hover` | `#8C2B27` | hover/down states |

**Status pigments (muted, shape+text always paired):**
| Token | Value | Use |
|---|---|---|
| `--color-ok` | `#2F6B47` | ok / available / on-time |
| `--color-ok-tint` | `#DCE8DF` | |
| `--color-warn` | `#8A6A1F` | warn / delayed / degraded |
| `--color-warn-tint` | `#F0E8CF` | |
| `--color-danger` | `#A6342F` | danger / critical / locked |
| `--color-danger-tint` | `#F0D9D4` | |
| `--color-info` | `#3A5C7A` | info / neutral |
| `--color-info-tint` | `#DCE5EC` | |

Contrast: all text pairs above AA; the ink pairs are ≥ 7:1 on canvas; status tokens are used
as tinted fill + text, with text on tint ≥ 4.5:1.

### 2.2 Typography

| Role | Family | Notes |
|---|---|---|
| UI text / labels / body | `system-ui, -apple-system, "Segoe UI", Roboto, sans-serif` | no webfont dependency (offline-safe) |
| Figures / IDs / numerics / timestamps / codes | `ui-monospace, "Cascadia Mono", "JetBrains Mono", Consolas, monospace` | 0.92em size compensation; `font-variant-numeric: tabular-nums` |

Scale (px | use):
- 24 / 600  — page title (developer: `--text-display`)
- 18 / 550  — section title, dialogs (`--text-title`)
- 14 / 500  — panel charts, table cells (`--text-body`)
- 13 / 500  — dense table cells, inputs (`--text-compact`)
- 12 / 600  — meta, timestamps (`--text-meta`)
- 11 / 600, 0.08em tracking, UPPERCASE  — kicker labels, micro-labels (`--text-kicker`)

### 2.3 Spacing, radius, elevation, motion

- Spacing scale: 4/8/12/16/24/32/48.
- Radius: `--radius = 2px` (slight); never > 4px.
- Elevation: panels are flat (1px rule top/left + 1px rule bottom/right); dropdowns/modals
  get `0 1px 2px rgba(28,30,31,0.18), 0 4px 12px rgba(28,30,31,0.16)`.
- Focus ring: `2px solid var(--color-accent)` inset + `2px offset`; always visible.
- Motion: hover/focus 120ms `ease-out`; panels 200ms; progress only on real data-change
  pulses (240ms fade, skip when `prefers-reduced-motion: reduce`).

---

## 3. Layout model

```
┌──────────────┬──────────────────────────────────────────────┐
│ Rail (232px) │ Top bar (56px): kicker+title · conn status ·  │
│  brand mark  │                  · role + user chip            │
│  nav items   ├──────────────────────────────────────────────┤
│  (icons+     │ Canvas: page content, 24px gutters,           │
│   mono label)│          fluid grid (GHC 12 / min 320)        │
│  footer:     │  panels, tables, dialogs                      │
│  role, ver   │                                              │
└──────────────┴──────────────────────────────────────────────┘
```

- Left rail fixed, dark shell. Active nav item: left 2px crimson rule + accent-tinted icon +
  shell-ink text; inactive items `--color-shell-ink-2`.
- Top bar: page kicker (`/name.upper` mono) + page title on the `--color-canvas` band;
  connection status dot (CONNECTED/DEGRADED/RECONNECTING); user chip (mono username + role badge).
- Content band uses `--color-canvas`; panels are `--color-surface` with hairline rules.
- Tables: sticky header, `--color-surface-2` header row, hairline dividers, mono cells for
  identifiers/numerics/timestamps, hover row tint `rgba(166,52,47,0.06)`.
- Empty / error / degraded states are first-class: centered glyph mark, kicker, message,
  recovery action.

---

## 4. State model (honest data)

Every data view implements the same contract:

```
idle → loading → (data | empty | error)  +  degraded(SHOWN when connector cannot reach WS/backend)
```

| State | Rendering | Recovery |
|---|---|---|
| `loading` | skeleton panels of the real final geometry | — |
| `empty` | EmptyState: glyph, kicker "NO RECORDS", guidance | filter reset / create CTA if permitted |
| `error` | ErrorPanel: RFC 9457 title/detail, 4xx/5xx distinction | Retry button |
| `degraded` | DegradedBanner atop content: "LIVE CONNECTION UNAVAILABLE — showing last known snapshot", never fabricates data | Reconnect on interval + manual Retry |

No fake data, no fake real-time. If the WS connector cannot connect (RabbitMQ/gateway down),
the UI says so and shows REST-derived snapshots or an explicit gap, labeled as such.

---

## 5. Architecture

```
src/
  app/            layout.tsx · providers.tsx · router.tsx     (routes + guards)
  main.tsx
  styles/         tokens.css · base.css · components.css · layout.css
  lib/            http.ts · token.ts · ws.ts · jwt.ts · guards.tsx
                  hooks.ts (useAsync, useRole, useRealtime)      (patterns)
  api/            authApi.ts · containerApi.ts · gateApi.ts
                  vesselApi.ts · yardApi.ts · notificationApi.ts  (typed, contract-faithful)
  ui/             Button · Badge · Panel · Table · Input · Select · Toggle · Switch
                  Modal · Pagination · Skeleton · ErrorPanel · EmptyState
                  StatusDot · DegradedBanner · Stack · Spinner · Kicker
  features/       auth/ dash/ containers/ yard/ gate/ vessels/
                  notifications/ admin/ system/ notfound/
  types/          api.ts · auth.ts · container.ts · gate.ts · yard.ts
                  vessel.ts · notification.ts · index.ts
  test/           setup.ts
```

- **Layers strictly**: UI → hooks → api/ws → http. Features never call axios directly.
- **Contracts**: every call targets the exact verified paths captured in the M11 inventory
  (e.g. `POST /api/v1/containers/search`, `GET /api/v1/yard/layout`, `PATCH /api/v1/schedules/{id}`).
  Response shapes follow `ListResponse<T>` (`items`/`nextCursor`), `{ pagination, data }`, and
  RFC 9457 `ProblemDetail` errors. No endpoint is invented.
- **Auth flow**: `/auth/login` → store tokens (sessionStorage — survives F5, cleared on tab
  close; flagged, not silent) → silent refresh on 401/expiry via `/auth/refresh` (single-flight
  queue) → 401-without-refresh → redirect `/login`. No `/auth/me` dependency: user object is
  restored from the persisted `LoginResponse`.
- **Role model**: `ADMIN > TERMINAL_OP > YARD_MANAGER/GATE_OPERATOR > READONLY`. Routes declare
  allowed roles; `RequireRoles` renders a 403 screen, never hides a broken page.
- **Realtime**: STOMP over SockJS through `/ws` proxy. Client owns a capability/service-health
  ledger; `topicAllowed(route, role)` gates subscriptions server-modelled on
  `api-gateway`'s route definitions. Reconnect with backoff; visible connection state always.
- **State management**: no external store; `useAsync` + React state. Single flight for refresh.

---

## 6. Accessibility

- Semantic landmarks (`<aside>`, `<nav>`, `<main>`, headers), skip-link.
- Full keyboard operability; visible focus rings; no role-only assumptions.
- `<th scope>`, caption for data tables; aria-live for connection state + live updates.
- All color usage passes 4.5:1 on its background; status never communicated by hue alone.
- Respect `prefers-reduced-motion`.

---

## 7. Non-goals (out of scope for this build)

- Real-time socket delivery when the backend/AMQP layer is down (we show honest degradation).
- Third-party chart libraries and webfonts (offline-safe, hand-built primitives).
- Any backend or contract modification; the gateway REST surface is the source of truth.