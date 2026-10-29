import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { UserResponse } from '../../types/auth'
import type { ProbeRun } from '../../api/systemApi'
import { SystemPage } from './SystemPage'

const {
  probeAllMock,
  allowedPrefixesMock,
  classifyPathMock,
  runTestMock,
} = vi.hoisted(() => ({
  probeAllMock: vi.fn(),
  allowedPrefixesMock: vi.fn(() => ['/v1/containers', '/v1/vessels']),
  classifyPathMock: vi.fn(() => ({ ok: true, path: '/v1/containers?page=1&size=1' }) as const),
  runTestMock: vi.fn(),
}))

vi.mock('../../api/systemApi', () => ({
  probeAll: probeAllMock,
  GATEWAY_ROUTES: [
    { path: '/api/v1/auth/**', upstream: 'identity', acl: 'public', fallback: '/fallback/identity', note: 'login · refresh · jwks' },
    { path: '/api/v1/users/**', upstream: 'identity', acl: ['ADMIN'], fallback: '/fallback/identity', note: 'user administration' },
    { path: '/ws/**', upstream: 'notification', acl: 'ws', fallback: null, note: 'STOMP over SockJS' },
  ],
  allowedPrefixesForRole: allowedPrefixesMock,
  classifyTestPath: classifyPathMock,
  runTest: runTestMock,
}))

vi.mock('../../lib/auth', () => ({
  useAuth: () => ({ user: adminUser(), isAuthenticated: true, login: vi.fn(), logout: vi.fn(), hasRole: () => true }),
}))

vi.mock('../../lib/realtime', () => ({
  useRealtime: () => ({ status: 'CONNECTED' }),
}))

function adminUser(): UserResponse {
  return {
    id: 'u-1',
    username: 'ops',
    displayName: 'Ops Lead',
    email: 'ops@example.com',
    role: 'ADMIN',
    status: 'ACTIVE',
    permissions: ['*'],
    lastLoginAt: '2026-01-02T03:04:05.000Z',
    createdAt: '2026-01-01T00:00:00.000Z',
    updatedAt: '2026-01-01T00:00:00.000Z',
  }
}

function feast(): ProbeRun[] {
  const base = {
    requiredRoles: ['ADMIN'],
    probedAt: new Date().toISOString(),
  } as const
  return [
    { ...base, service: 'smartportx-identity', description: 'jwks', probePath: '/v1/auth/jwks', verdict: 'up', httpStatus: 200, latencyMs: 12, note: 'HTTP 200 — live response' },
    { ...base, service: 'smartportx-container', description: 'containers', probePath: '/v1/containers?page=1&size=1', verdict: 'up', httpStatus: 200, latencyMs: 8, note: 'HTTP 200 — live response' },
    { ...base, service: 'smartportx-yard', description: 'yard', probePath: '/v1/yard/metrics?period=24h', verdict: 'up', httpStatus: 200, latencyMs: 20, note: 'HTTP 200 — live response' },
    { ...base, service: 'smartportx-gate', description: 'gate', probePath: '/v1/gate/queue', verdict: 'blocked', httpStatus: 403, latencyMs: 4, note: 'HTTP 403 — authorization blocked at gateway' },
    { ...base, service: 'smartportx-vessel', description: 'vessel', probePath: '/v1/vessels', verdict: 'unreachable', httpStatus: null, latencyMs: 3000, note: 'no response' },
    { ...base, service: 'smartportx-notification', description: 'prefs', probePath: '/v1/notifications/preferences', verdict: 'reachable', httpStatus: 404, latencyMs: 6, note: 'HTTP 404 — a service answered' },
    { ...base, service: 'smartportx-integration', description: 'integration', probePath: null, verdict: 'skipped', httpStatus: null, latencyMs: null, note: 'GET endpoints require path params — no auto-probe' },
  ]
}

function seedToken(props: Record<string, unknown>) {
  const b64 = btoa(JSON.stringify(props)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  window.sessionStorage.setItem('smartportx.access', `abc.${b64}.sig`)
}

beforeEach(() => {
  probeAllMock.mockReset().mockResolvedValue(feast())
  runTestMock.mockReset().mockResolvedValue({ ok: true, httpStatus: 200, latencyMs: 3, note: 'HTTP 200 OK', body: '{"items":[]}' })
  allowedPrefixesMock.mockReturnValue(['/v1/containers', '/v1/vessels'])
  classifyPathMock.mockReturnValue({ ok: true, path: '/v1/containers?page=1&size=1' })
})

describe('SystemPage', () => {
  it('renders the console with KPI + realtime chip', async () => {
    render(<SystemPage />)
    expect(screen.getByRole('heading', { name: 'Runtime console' })).toBeInTheDocument()
    await screen.findByText('smartportx-identity')
    expect(screen.getByText('CONNECTED')).toBeInTheDocument()
    expect(screen.getByText('ALIVE')).toBeInTheDocument()
  })

  it('classifies every probe verdict into an honest badge', async () => {
    render(<SystemPage />)
    await screen.findByText('smartportx-identity')
    expect(screen.getByText('UP')).toBeInTheDocument()
    expect(screen.getByText('403').closest('span')).toHaveClass('badge-warn')
    expect(screen.getByText('UNREACHABLE')).toBeInTheDocument()
    expect(screen.getByText('REACHABLE')).toBeInTheDocument()
    expect(screen.getByText('GET endpoints require path params — no auto-probe')).toBeInTheDocument()
  })

  it('derives the gateway summary from answered attempts only', async () => {
    render(<SystemPage />)
    await screen.findByText('smartportx-identity')
    expect(screen.getByText(/5 of 6 attempted routes answered \(3 HTTP 200\)/)).toBeInTheDocument()
  })

  it('lists the verified gateway route inventory', async () => {
    render(<SystemPage />)
    expect(await screen.findByText('/api/v1/auth/**')).toBeInTheDocument()
    expect(screen.getByText('PUBLIC').closest('span')).toHaveClass('badge-neutral')
    expect(screen.getByText('/ws/**')).toBeInTheDocument()
    expect(screen.getByText('STOMP over SockJS')).toBeInTheDocument()
  })

  it('shows the signed-in session fields', async () => {
    render(<SystemPage />)
    expect(await screen.findByText('ops')).toBeInTheDocument()
    expect(screen.getByText('ops@example.com')).toBeInTheDocument()
    expect(screen.getByText('ADMIN').closest('span')).toHaveClass('badge-accent')
  })

  it('masks the token by default and reveals on toggle', async () => {
    seedToken({ sub: 'u-1', username: 'ops', roles: ['ADMIN'], iat: 1, exp: 4102444800, iss: 'smartportx', aud: 'spa' })
    render(<SystemPage />)
    await screen.findByText('smartportx-identity')

    const tokenCell = screen.getByText(/^.•+$/)
    expect(tokenCell.textContent).toContain('•')
    expect(tokenCell.textContent).not.toContain('smartportx')

    await userEvent.click(screen.getByRole('button', { name: 'Reveal' }))
    expect(screen.getByText(/abstract\..*\.sig/)).toBeInTheDocument()
    expect(screen.getByText(/Token shown in full/)).toBeInTheDocument()
  })

  it('renders decoded claims from the token payload', async () => {
    seedToken({ sub: 'u-1', username: 'ops', roles: ['ADMIN'], iat: 1, exp: 4102444800, iss: 'smartportx', aud: 'spa' })
    render(<SystemPage />)
    expect(await screen.findByText('4102444800'.replace('', '') + (screen.queryAllByText('exp').length ? 'dummy' : ''))).toBeTruthy()
  })

  it('runs a gated GET and renders status + body', async () => {
    render(<SystemPage />)
    await screen.findByText('smartportx-identity')

    const input = screen.getByLabelText('Probe path')
    await userEvent.type(input, '/v1/containers?page=1&size=1')
    await userEvent.click(screen.getByRole('button', { name: /Run GET/ }))

    await waitFor(() => expect(classifyPathMock).toHaveBeenCalledWith('/v1/containers?page=1&size=1', 'ADMIN'))
    await screen.findByText('200 OK')
    expect(runTestMock).toHaveBeenCalledWith('/v1/containers?page=1&size=1')
    expect(screen.getByText('{"items":[]}')).toBeInTheDocument()
  })

  it('surfaces inline gating errors without calling the fetcher', async () => {
    classifyPathMock.mockReturnValue({ ok: false, error: 'Your role cannot call /v1/integration/customs/1 — allowed prefixes: /v1/containers.' })
    render(<SystemPage />)
    await screen.findByText('smartportx-identity')

    const input = screen.getByLabelText('Probe path')
    await userEvent.type(input, '/v1/integration/customs/1')
    await userEvent.click(screen.getByRole('button', { name: /Run GET/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Your role cannot call')
    expect(runTestMock).not.toHaveBeenCalled()
  })

  it('shows an honest "no browser route" marker on infrastructure rows', async () => {
    render(<SystemPage />)
    await screen.findByText('smartportx-identity')
    expect(screen.getByText('Spring Cloud Gateway')).toBeInTheDocument()
    expect(screen.getByText('localhost:5432')).toBeInTheDocument()
    const noRoutes = screen.getAllByText('—')
    expect(noRoutes.length).toBeGreaterThan(0)
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
  })

  it('shows a "no signed-in user" empty state without a session user', async () => {
    vi.mocked(useAuthStore).mockReturnValue({ user: null, isAuthenticated: false, login: vi.fn(), logout: vi.fn(), hasRole: () => false })
    render(<SystemPage />)
    expect(await screen.findByText('No signed-in user')).toBeInTheDocument()
  })
})