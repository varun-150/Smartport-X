// System module — runtime console.
//
// Live, role-aware status of the browser's real HTTP surface (the gateway), the verified
// gateway route table, signed-in session + masked JWT claims, and a role-gated read-only
// GET tester. Nothing here is invented: probe endpoints match actual controllers, the route
// inventory mirrors smartportx-gateway application.yml, and host-level components are
// labelled as "no browser route" rather than guessed as reachable. The login response role
// (server-issued) drives RBAC — decoded JWT claims are informational only.

import { useMemo, useState } from 'react'
import {
  GATEWAY_ROUTES,
  allowedPrefixesForRole,
  classifyTestPath,
  probeAll,
  runTest,
  type ProbeRun,
  type ProbeVerdict,
  type TestResult,
} from '../../api/systemApi'
import { env } from '../../config/env'
import { useAuth } from '../../lib/auth'
import { formatDateTime, formatDuration } from '../../lib/format'
import { useAsync, useInterval } from '../../lib/hooks'
import { decodeJwtPayload, maskToken } from '../../lib/jwt'
import { useRealtime, type RealtimeStatus } from '../../lib/realtime'
import { tokenStore } from '../../lib/token'
import { Button, Field, Input } from '../../ui/controls'
import { DegradedBanner, EmptyState, ErrorPanel, Kicker, Spinner, StatusDot } from '../../ui/primitives'

const PROBE_BADGE: Record<ProbeVerdict, { className: string; label: string }> = {
  up: { className: 'badge badge-ok', label: 'UP' },
  reachable: { className: 'badge badge-info', label: 'REACHABLE' },
  blocked: { className: 'badge badge-warn', label: '403' },
  down: { className: 'badge badge-danger', label: 'DOWN' },
  unreachable: { className: 'badge badge-danger', label: 'UNREACHABLE' },
  skipped: { className: 'badge badge-neutral', label: '—' },
}

const RT_TONE: Record<RealtimeStatus, { tone: 'ok' | 'warn' | 'danger' | 'info'; label: string }> = {
  CONNECTED: { tone: 'ok', label: 'CONNECTED' },
  CONNECTING: { tone: 'info', label: 'CONNECTING' },
  RECONNECTING: { tone: 'warn', label: 'RECONNECTING' },
  DEGRADED: { tone: 'warn', label: 'DEGRADED' },
  DISABLED: { tone: 'info', label: 'DISABLED' },
}

interface InfraRow {
  component: string
  detail: string
  url: string
  browser: boolean
}

const INFRASTRUCTURE: InfraRow[] = [
  { component: 'Gateway', detail: 'Spring Cloud Gateway', url: 'http://localhost:8080', browser: true },
  { component: 'PostgreSQL', detail: 'shared database', url: 'localhost:5432', browser: false },
  { component: 'RabbitMQ', detail: 'broker · STOMP', url: 'http://localhost:15672', browser: false },
  { component: 'Eureka', detail: 'service registry', url: 'http://localhost:8761', browser: false },
  { component: 'Prometheus', detail: 'metrics', url: 'http://localhost:9090', browser: false },
  { component: 'Grafana', detail: 'dashboards', url: 'http://localhost:3001', browser: false },
  { component: 'Jaeger', detail: 'tracing', url: 'http://localhost:16686', browser: false },
  { component: 'Dashboard (frontend)', detail: 'SPA — dev / E2E', url: 'http://localhost:3000', browser: true },
]

function aclLabel(acl: readonly string[] | 'public' | 'ws'): { text: string; className: string } {
  if (acl === 'public') return { text: 'PUBLIC', className: 'badge badge-neutral' }
  if (acl === 'ws') return { text: 'WS', className: 'badge badge-info' }
  return { text: acl.join(' · '), className: 'mono' }
}

export function SystemPage() {
  const { user } = useAuth()
  const realtime = useRealtime()
  const role = user?.role ?? 'READONLY'
  const { data: probes, error, loading, reload } = useAsync(() => probeAll(role), [role])
  useInterval(() => {
    void reload()
  }, 60_000)

  const [accessToken] = useState(() => tokenStore.getAccessToken())
  const [showToken, setShowToken] = useState(false)
  const claims = useMemo(() => decodeJwtPayload(accessToken), [accessToken])

  const [testPath, setTestPath] = useState('')
  const [testResult, setTestResult] = useState<TestResult | null>(null)
  const [testError, setTestError] = useState<string | null>(null)
  const [running, setRunning] = useState(false)

  const allowedPrefixes = useMemo(() => allowedPrefixesForRole(role), [role])

  const answered = probes?.filter((p) => p.httpStatus !== null).length ?? 0
  const count = probes?.length ?? 0
  const gatewayAlive = probes ? answered > 0 : false

  const runProbeTester = async () => {
    setTestError(null)
    setTestResult(null)
    const classified = classifyTestPath(testPath, role)
    if (!classified.ok) {
      setTestError(classified.error)
      return
    }
    setRunning(true)
    try {
      setTestResult(await runTest(classified.path))
    } finally {
      setRunning(false)
    }
  }

  const expIn = claims?.exp ? formatDuration(claims.exp * 1000 - Date.now()) : null

  return (
    <div className="page">
      <div className="page-intro">
        <Kicker>System</Kicker>
        <h1>Runtime console</h1>
      </div>

      {realtime.status !== 'CONNECTED' ? (
        <DegradedBanner text={`Realtime messaging ${realtime.status.toLowerCase()} — live events from the gateway are unavailable.`} />
      ) : null}

      <section className="kpis" aria-label="Runtime status">
        <div className="kpi">
          <span className="kpi-label">Realtime</span>
          <span className="kpi-value">
            <StatusDot tone={RT_TONE[realtime.status].tone} label={RT_TONE[realtime.status].label} />
          </span>
          <span className="kpi-hint">broker link</span>
        </div>
        <div className="kpi">
          <span className="kpi-label">Gateway</span>
          <span className="kpi-value">{gatewayAlive ? 'ALIVE' : '—'}</span>
          <span className="kpi-hint">via {env.apiBaseUrl}</span>
        </div>
        <div className="kpi">
          <span className="kpi-label">Probes answered</span>
          <span className="kpi-value mono">{`${answered} / ${count}`}</span>
          <span className="kpi-hint">HTTP responses via gateway</span>
        </div>
        <div className="kpi">
          <span className="kpi-label">Signed in as</span>
          <span className="kpi-value mono" style={{ fontSize: 18 }}>{user?.username ?? '—'}</span>
          <span className="kpi-hint">{role}</span>
        </div>
      </section>

      <section>
        <div className="panel-header">
          <h2 className="panel-title">Service probes</h2>
          <span className="hstack" style={{ gap: 10 }}>
            <span className="mono" style={{ fontSize: 12, color: 'var(--color-ink-3)' }}>
              GET-only · auto-refresh 60 s · timeout {env.requestTimeoutMs} ms
            </span>
            <Button variant="ghost" size="sm" onClick={() => void reload()} disabled={loading}>
              Refresh
            </Button>
          </span>
        </div>

        {loading ? (
          <Spinner label="Probing services" />
        ) : error ? (
          <ErrorPanel error={error} onRetry={reload} />
        ) : probes ? (
          <>
            <div style={{ padding: '0 16px 10px', fontSize: 13, color: 'var(--color-ink-3)' }}>
              Probes pass through the gateway at {env.apiBaseUrl} with your token. A 403 means the
              gateway refused the route before reaching the service; host-level ports are not
              callable from the browser.
            </div>
            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th>Service</th>
                    <th>Probe path</th>
                    <th>Verdict</th>
                    <th className="num th">Latency</th>
                    <th>Detail</th>
                  </tr>
                </thead>
                <tbody>
                  {probes.map((run) => (
                    <tr key={run.service}>
                      <td>
                        <span className="mono">{run.service}</span>
                        <div style={{ fontSize: 12, color: 'var(--color-ink-3)' }}>{run.description}</div>
                      </td>
                      <td className="mono" style={{ fontSize: 12 }}>{run.probePath ?? '— no probe'}</td>
                      <td>
                        <span className={PROBE_BADGE[run.verdict].className}>{PROBE_BADGE[run.verdict].label}</span>
                      </td>
                      <td className="num mono">{run.latencyMs === null ? '—' : `${run.latencyMs} ms`}</td>
                      <td style={{ fontSize: 12, color: 'var(--color-ink-3)' }}>{run.note}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <GatewaySummaryRow probes={probes} />
          </>
        ) : null}
      </section>

      <section>
        <div className="panel-header">
          <h2 className="panel-title">Gateway routes</h2>
          <span className="mono" style={{ fontSize: 12, color: 'var(--color-ink-3)' }}>
            verified application.yml · {GATEWAY_ROUTES.length} routes
          </span>
        </div>
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr>
                <th>Path</th>
                <th>Upstream</th>
                <th>Access</th>
                <th>Fallback</th>
              </tr>
            </thead>
            <tbody>
              {GATEWAY_ROUTES.map((route) => {
                const acl = aclLabel(route.acl)
                return (
                  <tr key={route.path}>
                    <td className="mono" style={{ fontSize: 12 }}>{route.path}</td>
                    <td><span className="mono">{route.upstream}</span>{route.note ? <div style={{ fontSize: 12, color: 'var(--color-ink-3)' }}>{route.note}</div> : null}</td>
                    <td><span className={acl.className}>{acl.text}</span></td>
                    <td className="mono" style={{ fontSize: 12 }}>{route.fallback ?? '—'}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <div className="panel-header">
          <h2 className="panel-title">Signed-in session</h2>
          <span className="mono" style={{ fontSize: 12, color: 'var(--color-ink-3)' }}>
            role-driven · server-issued
          </span>
        </div>
        <div style={{ padding: 16 }}>
          {user ? (
            <dl className="meta">
              <dt>Username</dt>
              <dd>{user.username}</dd>
              <dt>Display name</dt>
              <dd>{user.displayName || '—'}</dd>
              <dt>Email</dt>
              <dd>{user.email || '—'}</dd>
              <dt>Role</dt>
              <dd><span className={`badge ${user.role === 'ADMIN' ? 'badge-accent' : 'badge-info'}`}>{user.role}</span></dd>
              <dt>Permissions</dt>
              <dd>{user.permissions.length > 0 ? user.permissions.join(' · ') : '—'}</dd>
              <dt>Last login</dt>
              <dd>{user.lastLoginAt ? formatDateTime(user.lastLoginAt) : '—'}</dd>
            </dl>
          ) : (
            <EmptyState title="No signed-in user" hint="Log in to probe routes as your role." />
          )}
        </div>
      </section>

      <section>
        <div className="panel-header">
          <h2 className="panel-title">Access token (JWT)</h2>
          <span className="hstack" style={{ gap: 10 }}>
            <span className="badge badge-warn">masked</span>
            <Button variant="ghost" size="sm" onClick={() => setShowToken((v) => !v)}>
              {showToken ? 'Hide' : 'Reveal'}
            </Button>
          </span>
        </div>
        <div style={{ padding: 16 }}>
          <div className="mono" style={{ fontSize: 12, color: 'var(--color-ink-3)', wordBreak: 'break-all' }}>
            {accessToken ? (showToken ? accessToken : maskToken(accessToken)) : '— no access token'}
          </div>
          <div style={{ fontSize: 12, color: 'var(--color-ink-3)', marginTop: 6 }}>
            {showToken ? 'Token shown in full — never pasted into a terminal. Close this tab to clear the session.' : 'Masked by default — only the payload below is decoded, and only for display.'}
          </div>
        </div>

        {claims ? (
          <div className="table-wrap" style={{ padding: '0 16px 16px' }}>
            <table className="data">
              <thead>
                <tr>
                  <th>Claim</th>
                  <th>Value</th>
                </tr>
              </thead>
              <tbody>
                <tr><td className="mono">sub</td><td className="mono">{claims.sub ?? '—'}</td></tr>
                <tr><td className="mono">username</td><td className="mono">{claims.username ?? '—'}</td></tr>
                <tr><td className="mono">roles</td><td><span className={claims.roles ? 'mono' : ''}>{claims.roles?.join(' · ') ?? '—'}</span></td></tr>
                <tr><td className="mono">iat</td><td className="mono">{claims.iat ? formatDateTime(new Date(claims.iat * 1000).toISOString()) : '—'}</td></tr>
                <tr><td className="mono">exp</td><td className="mono">{claims.exp ? `${formatDateTime(new Date(claims.exp * 1000).toISOString())} (${expIn ?? 'expired'})` : '—'}</td></tr>
                <tr><td className="mono">iss</td><td className="mono">{claims.iss ?? '—'}</td></tr>
                <tr><td className="mono">aud</td><td className="mono">{Array.isArray(claims.aud) ? claims.aud.join(' · ') : (claims.aud ?? '—')}</td></tr>
                <tr><td className="mono">jti</td><td className="mono">{claims.jti ?? '—'}</td></tr>
              </tbody>
            </table>
          </div>
        ) : (
          <div style={{ padding: '0 16px 16px' }}>
            <EmptyState title="Token does not decode to a three-part JWT" hint="The persisted session may predate a token format change." />
          </div>
        )}
      </section>

      <section>
        <div className="panel-header">
          <h2 className="panel-title">Read-only GET tester</h2>
          <span className="badge badge-info">GET only</span>
        </div>
        <div style={{ padding: 16 }}>
          <div className="vstack" style={{ gap: 12 }}>
            <Field
              label="Probe path"
              hint={`Allowed prefixes for ${role}: ${allowedPrefixes.join(' · ') || 'none'}`}
              error={testError}
            >
              <Input
                value={testPath}
                onChange={(e) => setTestPath(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && testPath.trim() && !running) void runProbeTester()
                }}
                placeholder="/v1/containers?page=1&size=1"
                invalid={Boolean(testError)}
                aria-label="Probe path"
                spellCheck={false}
              />
            </Field>
            <div className="hstack" style={{ gap: 10 }}>
              <Button variant="primary" size="sm" onClick={() => void runProbeTester()} disabled={running || !testPath.trim()}>
                {running ? 'Running…' : 'Run GET'}
              </Button>
              <span className="mono" style={{ fontSize: 12, color: 'var(--color-ink-3)' }}>
                timeboxed to {env.requestTimeoutMs} ms · body capped at 4 KB
              </span>
            </div>
          </div>

          {testResult ? (
            <div className="vstack" style={{ gap: 10, marginTop: 16 }}>
              <div className="hstack" style={{ gap: 12 }}>
                <span className={testResult.ok ? 'badge badge-ok' : 'badge badge-danger'}>
                  {testResult.ok ? '200 OK' : `${testResult.httpStatus ?? '—'} FAILED`}
                </span>
                <span className="mono" style={{ fontSize: 12 }}>{testResult.latencyMs} ms</span>
                <span style={{ fontSize: 13, color: 'var(--color-ink-3)' }}>{testResult.note}</span>
              </div>
              {testResult.body !== null ? (
                <div
                  className="mono"
                  style={{
                    fontSize: 12,
                    lineHeight: 1.5,
                    maxHeight: 260,
                    overflow: 'auto',
                    padding: 10,
                    background: 'var(--color-surface-2)',
                    border: '1px solid var(--color-rule)',
                    borderRadius: 6,
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                    margin: 0,
                  }}
                >
                  {testResult.body}
                </div>
              ) : null}
            </div>
          ) : null}
        </div>
      </section>

      <section>
        <div className="panel-header">
          <h2 className="panel-title">Infrastructure</h2>
          <span className="badge badge-neutral">no browser route</span>
        </div>
        <div style={{ padding: '0 16px 16px' }}>
          <div style={{ fontSize: 13, color: 'var(--color-ink-3)', padding: '12px 0' }}>
            Host-level ports used by the demo stack. Only the gateway and SPA are reachable from
            the browser — the others are address-bar entries, not probed endpoints.
          </div>
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>Component</th>
                  <th>Detail</th>
                  <th>Address</th>
                  <th>Browser route</th>
                </tr>
              </thead>
              <tbody>
                {INFRASTRUCTURE.map((row) => (
                  <tr key={row.component}>
                    <td><span className="mono">{row.component}</span></td>
                    <td style={{ fontSize: 12, color: 'var(--color-ink-3)' }}>{row.detail}</td>
                    <td className="mono" style={{ fontSize: 12 }}>{row.url}</td>
                    <td><span className={row.browser ? 'badge badge-ok' : 'badge badge-neutral'}>{row.browser ? 'YES' : '—'}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </div>
  )
}

function GatewaySummaryRow({ probes }: { probes: ProbeRun[] }) {
  const attempted = probes.filter((p) => p.verdict !== 'skipped').length
  const answered = probes.filter((p) => p.httpStatus !== null).length
  const up = probes.filter((p) => p.verdict === 'up').length
  const label = `${answered} of ${attempted} attempted routes answered (${up} HTTP 200)`
  return (
    <div className="hstack" style={{ gap: 8, padding: '10px 16px 14px' }}>
      <span className={answered > 0 ? 'badge badge-ok' : 'badge badge-danger'}>
        {answered > 0 ? 'ALIVE' : 'DOWN'}
      </span>
      <span style={{ fontSize: 13, color: 'var(--color-ink-3)' }}>{label} — gateway reachable when any probe answers.</span>
    </div>
  )
}