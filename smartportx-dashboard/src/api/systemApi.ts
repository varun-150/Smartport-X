// Runtime console data access.
//
// Verified against the gateway route table (smartportx-gateway `application.yml`) and the
// service controllers that actually expose each GET. Probes go through the browser's only
// HTTP surface — the `/api` base (Vite dev proxy / Nginx -> gateway:8080). No fabricated
// endpoints: rows that cannot be safely probed from the SPA say so.

import { get, getErrorMessage } from '../lib/http'
import type { RoleName } from '../types'
import { ApiError } from '../types/api'

export type ProbeVerdict = 'up' | 'reachable' | 'blocked' | 'down' | 'unreachable' | 'skipped'

export interface ProbeTarget {
  service: string
  description: string
  /** Parameterless GET, null when no role-safe auto-probe exists. */
  probePath: string | null
  requiredRoles: readonly RoleName[]
  note?: string
}

export interface ProbeRun extends ProbeTarget {
  verdict: ProbeVerdict
  httpStatus: number | null
  latencyMs: number | null
  note: string
  probedAt: string | null
}

export const ALL_ROLES: readonly RoleName[] = [
  'ADMIN',
  'TERMINAL_OP',
  'YARD_MANAGER',
  'GATE_OPERATOR',
  'READONLY',
]

export function roleAllows(allowed: readonly RoleName[], role: RoleName): boolean {
  return allowed.includes(role)
}

/**
 * One GET probe per service, matched to a controller endpoint verified to exist and to be
 * reachable by the listed roles through the gateway. Integration has no parameterless GET
 * (CustomsController / EdifactController GETs require path params) — hence `probePath: null`.
 */
export const PROBE_TARGETS: readonly ProbeTarget[] = [
  {
    service: 'smartportx-identity',
    description: 'Auth — JWKS discovery',
    probePath: '/v1/auth/jwks',
    requiredRoles: ALL_ROLES,
    note: 'public route',
  },
  {
    service: 'smartportx-container',
    description: 'Container register',
    probePath: '/v1/containers?page=1&size=1',
    requiredRoles: ALL_ROLES,
    note: 'verified list GET',
  },
  {
    service: 'smartportx-yard',
    description: 'Yard block metrics',
    probePath: '/v1/yard/metrics?period=24h',
    requiredRoles: ['ADMIN', 'TERMINAL_OP', 'YARD_MANAGER', 'READONLY'],
    note: 'verified metrics GET',
  },
  {
    service: 'smartportx-gate',
    description: 'Gate operations queue',
    probePath: '/v1/gate/queue',
    requiredRoles: ['ADMIN', 'TERMINAL_OP', 'GATE_OPERATOR', 'READONLY'],
    note: 'verified queue GET',
  },
  {
    service: 'smartportx-vessel',
    description: 'Vessel list',
    probePath: '/v1/vessels',
    requiredRoles: ['ADMIN', 'TERMINAL_OP', 'READONLY'],
    note: 'verified list GET',
  },
  {
    service: 'smartportx-notification',
    description: 'Notification preferences',
    probePath: '/v1/notifications/preferences',
    requiredRoles: ALL_ROLES,
    note: 'verified preferences GET',
  },
  {
    service: 'smartportx-integration',
    description: 'Customs EDIFACT / REST broker',
    probePath: null,
    requiredRoles: ['ADMIN'],
    note: 'GET endpoints require path params — no auto-probe',
  },
]

/** Run one probe. Pass an injected fetcher in tests; the production path uses `lib/http`. */
export async function runProbe(
  target: ProbeTarget,
  role: RoleName,
  fetchFn: (path: string) => Promise<unknown> = get<unknown>,
): Promise<ProbeRun> {
  const base: ProbeRun = {
    ...target,
    verdict: 'skipped',
    httpStatus: null,
    latencyMs: null,
    note: target.note ?? '',
    probedAt: null,
  }

  if (!target.probePath) {
    return { ...base, note: target.note ?? 'No parameterless GET probe available.' }
  }
  if (!roleAllows(target.requiredRoles, role)) {
    return {
      ...base,
      note: `Probe requires ${target.requiredRoles.join(' / ')} — your role: ${role}.`,
    }
  }

  const started = performance.now()
  try {
    await fetchFn(target.probePath)
    return {
      ...base,
      verdict: 'up',
      httpStatus: 200,
      latencyMs: Math.round(performance.now() - started),
      note: 'HTTP 200 — live response',
      probedAt: new Date().toISOString(),
    }
  } catch (err) {
    return classify(err, base, Math.round(performance.now() - started))
  }
}

/** Classify a probe failure. A 403 from the gateway means the RoleRouter refused BEFORE
 *  forwarding — the proxy is alive but the probe did not reach the service. */
function classify(err: unknown, run: ProbeRun, latencyMs: number): ProbeRun {
  const probedAt = new Date().toISOString()
  if (err instanceof ApiError) {
    const detail = getErrorMessage(err)
    if (err.status === 0) {
      return {
        ...run,
        verdict: 'unreachable',
        latencyMs,
        note: `No response — ${detail}`,
        probedAt,
      }
    }
    if (err.status === 403) {
      return {
        ...run,
        verdict: 'blocked',
        httpStatus: 403,
        latencyMs,
        note: 'HTTP 403 — authorization blocked at gateway (service not probed)',
        probedAt,
      }
    }
    if (err.status >= 500) {
      return {
        ...run,
        verdict: 'down',
        httpStatus: err.status,
        latencyMs,
        note: err.isNetwork
          ? 'Gateway reached but no upstream — circuit/failure'
          : `HTTP ${err.status} — gateway fallback / upstream failure`,
        probedAt,
      }
    }
    return {
      ...run,
      verdict: 'reachable',
      httpStatus: err.status,
      latencyMs,
      note: `HTTP ${err.status} — a service answered through the gateway`,
      probedAt,
    }
  }
  return {
    ...run,
    verdict: 'unreachable',
    latencyMs,
    note: err instanceof Error ? err.message : 'Unknown error',
    probedAt,
  }
}

/** Run every probe for the signed-in role. Never rejects — failures become verdict rows. */
export async function probeAll(role: RoleName): Promise<ProbeRun[]> {
  return Promise.all(PROBE_TARGETS.map((target) => runProbe(target, role)))
}

/* ------------------------------ Gateway inventory ------------------------------ */

export type GatewayAcl = readonly RoleName[] | 'public' | 'ws'

export interface GatewayRoute {
  path: string
  upstream: string
  acl: GatewayAcl
  fallback: string | null
  note?: string
}

/** Mirror of the verified gateway route table (smartportx-gateway application.yml). */
export const GATEWAY_ROUTES: readonly GatewayRoute[] = [
  {
    path: '/api/v1/auth/**',
    upstream: 'identity',
    acl: 'public',
    fallback: '/fallback/identity',
    note: 'login · refresh · jwks',
  },
  {
    path: '/api/v1/users/**',
    upstream: 'identity',
    acl: ['ADMIN'],
    fallback: '/fallback/identity',
    note: 'user administration',
  },
  {
    path: '/api/v1/containers/**',
    upstream: 'container',
    acl: ALL_ROLES,
    fallback: '/fallback/container',
  },
  {
    path: '/api/v1/yard/**',
    upstream: 'yard',
    acl: ['ADMIN', 'TERMINAL_OP', 'YARD_MANAGER', 'READONLY'],
    fallback: '/fallback/yard',
  },
  {
    path: '/api/v1/gate/**, /api/v1/appointments/**, /api/v1/quotas/**',
    upstream: 'gate',
    acl: ['ADMIN', 'TERMINAL_OP', 'GATE_OPERATOR', 'READONLY'],
    fallback: '/fallback/gate',
  },
  {
    path: '/api/v1/vessels/**, /api/v1/schedules/**, /api/v1/discharge-plans/**',
    upstream: 'vessel',
    acl: ['ADMIN', 'TERMINAL_OP', 'READONLY'],
    fallback: '/fallback/vessel',
  },
  {
    path: '/api/v1/integration/**',
    upstream: 'integration',
    acl: ['ADMIN'],
    fallback: '/fallback/integration',
  },
  {
    path: '/api/v1/notifications/**',
    upstream: 'notification',
    acl: ALL_ROLES,
    fallback: '/fallback/notification',
  },
  {
    path: '/api/v1/testimonials/**',
    upstream: 'notification',
    acl: ALL_ROLES,
    fallback: '/fallback/notification',
  },
  {
    path: '/ws/**',
    upstream: 'notification',
    acl: 'ws',
    fallback: null,
    note: 'STOMP over SockJS',
  },
]

/* ------------------------------ Safe GET tester ------------------------------ */

function routePrefixes(route: GatewayRoute): string[] {
  return route.path
    .split(',')
    .map((s) => s.trim())
    .filter((p) => p.startsWith('/api/v1/'))
    .map((p) => p.replace(/\/\*\*?$/, '').replace(/^\/api/, ''))
}

/** Prefixes of gateway routes the given role may call, for tester gating. */
export function allowedPrefixesForRole(role: RoleName): string[] {
  const prefixes: string[] = []
  for (const route of GATEWAY_ROUTES) {
    if (typeof route.acl === 'string') {
      if (route.acl === 'public') prefixes.push(...routePrefixes(route))
      continue
    }
    if (roleAllows(route.acl, role)) prefixes.push(...routePrefixes(route))
  }
  return [...new Set(prefixes)]
}

export type ClassifiedPath = { ok: true; path: string } | { ok: false; error: string }

/** Normalize + gate a tester path to a prefix (with boundary) the role may call. */
export function classifyTestPath(input: string, role: RoleName): ClassifiedPath {
  const raw = input.trim()
  if (!raw) return { ok: false, error: 'Enter a path to run.' }
  const stripped = raw.startsWith('/api') ? raw.replace(/^\/api/, '') : raw
  const [pathPart, query] = stripped.split('?')
  if (!pathPart || !pathPart.startsWith('/v1/')) {
    return { ok: false, error: 'Path must start with /v1/ (or /api/v1/).' }
  }
  if (pathPart.split('/').some((seg) => seg.includes('*'))) {
    return { ok: false, error: 'Wildcards are not allowed.' }
  }
  const allowed = allowedPrefixesForRole(role)
  const hit = allowed.some((prefix) => pathPart === prefix || pathPart.startsWith(prefix + '/'))
  if (!hit) {
    return {
      ok: false,
      error: `Your role cannot call ${pathPart} — allowed prefixes: ${allowed.join(' · ') || 'none'}.`,
    }
  }
  const path = query === undefined ? pathPart : `${pathPart}?${query}`
  return { ok: true, path }
}

export interface TestResult {
  ok: boolean
  httpStatus: number | null
  latencyMs: number
  note: string
  body: string | null
}

/** Execute one validated GET through the gateway. Body is capped for display. */
export async function runTest(path: string): Promise<TestResult> {
  const started = performance.now()
  try {
    const data = await get<unknown>(path)
    const body = JSON.stringify(data)
    return {
      ok: true,
      httpStatus: 200,
      latencyMs: Math.round(performance.now() - started),
      note: 'HTTP 200 OK',
      body: body.length > 4000 ? `${body.slice(0, 4000)}\n… (truncated)` : body,
    }
  } catch (err) {
    const detail = err instanceof ApiError ? getErrorMessage(err) : err instanceof Error ? err.message : 'Unknown error'
    const status = err instanceof ApiError ? err.status : null
    return {
      ok: false,
      httpStatus: status || null,
      latencyMs: Math.round(performance.now() - started),
      note: detail,
      body: null,
    }
  }
}