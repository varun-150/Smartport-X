import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../types/api'
import {
  ALL_ROLES,
  GATEWAY_ROUTES,
  PROBE_TARGETS,
  allowedPrefixesForRole,
  classifyTestPath,
  probeAll,
  roleAllows,
  runProbe,
  runTest,
} from './systemApi'

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }))
vi.mock('../lib/http', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../lib/http')>()
  return { ...actual, get: getMock }
})

describe('roleAllows', () => {
  it('checks membership across the five role names', () => {
    expect(roleAllows(ALL_ROLES, 'READONLY')).toBe(true)
    expect(roleAllows(ALL_ROLES, 'ADMIN')).toBe(true)
    expect(roleAllows(['ADMIN', 'TERMINAL_OP', 'READONLY'], 'YARD_MANAGER')).toBe(false)
  })
})

describe('runProbe', () => {
  const yardTarget = PROBE_TARGETS.find((t) => t.service === 'smartportx-yard')!

  it('marks up on a 2xx response with latency', async () => {
    const result = await runProbe(yardTarget, 'ADMIN', () => Promise.resolve({ ok: true }))
    expect(result.verdict).toBe('up')
    expect(result.httpStatus).toBe(200)
    expect(result.latencyMs).toBeGreaterThanOrEqual(0)
    expect(result.probedAt).toBeTruthy()
  })

  it('skips when the role may not call the probe', async () => {
    const result = await runProbe(yardTarget, 'GATE_OPERATOR', () => Promise.resolve())
    expect(result.verdict).toBe('skipped')
    expect(result.httpStatus).toBeNull()
    expect(result.note).toContain('requires')
  })

  it('skips targets that have no parameterless GET (integration)', async () => {
    const integration = PROBE_TARGETS.find((t) => t.service === 'smartportx-integration')!
    const result = await runProbe(integration, 'ADMIN', () => Promise.resolve())
    expect(result.verdict).toBe('skipped')
    expect(result.note).toContain('no auto-probe')
  })

  it('classifies 403 as blocked at the gateway', async () => {
    const result = await runProbe(yardTarget, 'ADMIN', () => Promise.reject(new ApiError(403, 'Forbidden', 'nope')))
    expect(result.verdict).toBe('blocked')
    expect(result.httpStatus).toBe(403)
  })

  it('classifies a 4xx answered by a service as reachable', async () => {
    const result = await runProbe(yardTarget, 'ADMIN', () => Promise.reject(new ApiError(404, 'Not Found')))
    expect(result.verdict).toBe('reachable')
    expect(result.httpStatus).toBe(404)
  })

  it('classifies a gateway 5xx as down', async () => {
    const result = await runProbe(yardTarget, 'ADMIN', () => Promise.reject(new ApiError(502, 'Bad Gateway', undefined, undefined, true)))
    expect(result.verdict).toBe('down')
    expect(result.httpStatus).toBe(502)
  })

  it('classifies a network failure (status 0) as unreachable', async () => {
    const result = await runProbe(yardTarget, 'ADMIN', () => Promise.reject(new ApiError(0, 'Network Error', 'blocked')))
    expect(result.verdict).toBe('unreachable')
    expect(result.httpStatus).toBeNull()
  })

  it('classifies a raw error as unreachable', async () => {
    const result = await runProbe(yardTarget, 'ADMIN', () => Promise.reject(new Error('boom')))
    expect(result.verdict).toBe('unreachable')
    expect(result.note).toContain('boom')
  })
})

describe('probeAll', () => {
  it('runs every target through the production fetcher and never rejects', async () => {
    getMock.mockResolvedValue({ items: [] })
    const results = await probeAll('ADMIN')
    expect(results).toHaveLength(PROBE_TARGETS.length)
    const integration = results.find((r) => r.service === 'smartportx-integration')!
    expect(integration.verdict).toBe('skipped')
    const live = results.filter((r) => r.verdict === 'up')
    expect(live).toHaveLength(PROBE_TARGETS.length - 1)
  })
})

describe('GATEWAY_ROUTES', () => {
  it('mirrors the verified ten-route table', () => {
    expect(GATEWAY_ROUTES).toHaveLength(10)
    expect(GATEWAY_ROUTES.some((r) => r.path === '/ws/**')).toBe(true)
    expect(GATEWAY_ROUTES.find((r) => r.path === '/api/v1/users/**')?.acl).toEqual(['ADMIN'])
  })
})

describe('allowedPrefixesForRole', () => {
  it('grants ADMIN access to users and integration prefixes', () => {
    const prefixes = allowedPrefixesForRole('ADMIN')
    expect(prefixes).toContain('/v1/users')
    expect(prefixes).toContain('/v1/integration')
    expect(prefixes).toContain('/v1/gate')
  })

  it('keeps privileged prefixes away from READONLY', () => {
    const prefixes = allowedPrefixesForRole('READONLY')
    expect(prefixes).not.toContain('/v1/users')
    expect(prefixes).not.toContain('/v1/integration')
  })
})

describe('classifyTestPath', () => {
  it('accepts a public + role-allowed path with a query string', () => {
    const r = classifyTestPath('/v1/containers?page=1&size=1', 'READONLY')
    expect(r.ok).toBe(true)
    if (r.ok) expect(r.path).toBe('/v1/containers?page=1&size=1')
  })

  it('strips a leading /api prefix', () => {
    const r = classifyTestPath('/api/v1/auth/jwks', 'READONLY')
    expect(r.ok).toBe(true)
    if (r.ok) expect(r.path).toBe('/v1/auth/jwks')
  })

  it('rejects paths outside the role prefix set', () => {
    const r = classifyTestPath('/v1/integration/customs/123', 'READONLY')
    expect(r.ok).toBe(false)
    if (!r.ok) expect(r.error).toContain('cannot call')
  })

  it('rejects a prefix-adjacent but distinct path (boundary rule)', () => {
    const r = classifyTestPath('/v1/gateway', 'READONLY')
    expect(r.ok).toBe(false)
  })

  it('rejects /v1/gate for YARD_MANAGER', () => {
    const r = classifyTestPath('/v1/gate/queue', 'YARD_MANAGER')
    expect(r.ok).toBe(false)
  })

  it('rejects wildcards, wrong base, and empty input', () => {
    expect(classifyTestPath('/v1/containers/*', 'ADMIN').ok).toBe(false)
    expect(classifyTestPath('/containers', 'ADMIN').ok).toBe(false)
    const empty = classifyTestPath('   ', 'ADMIN')
    if (!empty.ok) expect(empty.error).toContain('Enter a path')
  })
})

describe('runTest', () => {
  it('returns status + body on success', async () => {
    getMock.mockResolvedValue({ items: [] })
    const r = await runTest('/v1/containers?page=1&size=1')
    expect(r.ok).toBe(true)
    expect(r.httpStatus).toBe(200)
    expect(r.body).toContain('items')
  })

  it('returns status + note on ApiError without rejecting', async () => {
    getMock.mockRejectedValue(new ApiError(502, 'Bad Gateway', 'upstream down', undefined, true))
    const r = await runTest('/v1/containers')
    expect(r.ok).toBe(false)
    expect(r.httpStatus).toBe(502)
    expect(r.note).toContain('upstream down')
    expect(r.body).toBeNull()
  })
})