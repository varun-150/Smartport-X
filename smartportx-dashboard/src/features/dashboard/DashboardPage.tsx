// Terminal overview — aggregates live snapshots across container, vessel, gate,
// and yard services, plus a realtime activity feed from the gateway.
//
// The gateway exposes no dedicated dashboard REST endpoint, so the page reads
// four service endpoints (each with honest loading/error/empty states) and shows
// broadcast events as they arrive. No fabricated numbers.

import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { containerApi } from '../../api/containerApi'
import { gateApi } from '../../api/gateApi'
import { vesselApi } from '../../api/vesselApi'
import { yardApi } from '../../api/yardApi'
import { formatDateTime, formatNumber, formatPercent } from '../../lib/format'
import { useAsync } from '../../lib/hooks'
import { useRealtime } from '../../lib/realtime'
import { DegradedBanner, EmptyState, ErrorPanel, Kicker, Spinner } from '../../ui/primitives'
import { Button } from '../../ui/controls'

interface DashboardSnapshot {
  containerCount: number
  vesselCount: number
  appointmentCount: number
  utilizationRate: number
  movesPerHour: number
  measuredAt: string | null
}

async function loadSnapshot(): Promise<DashboardSnapshot> {
  const [containers, vessels, appointments, yard] = await Promise.all([
    containerApi.list({ page: 1, size: 1 }),
    vesselApi.list(),
    gateApi.list({ page: 1, size: 1 }),
    yardApi.metrics('24h'),
  ])
  return {
    containerCount: containers.pagination.totalCount,
    vesselCount: vessels.totalCount,
    appointmentCount: appointments.pagination.totalCount,
    utilizationRate: yard.utilizationRate,
    movesPerHour: yard.movesPerHour,
    measuredAt: yard.measuredAt,
  }
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function toStringified(payload: unknown): string {
  try {
    return JSON.stringify(payload)
  } catch {
    return String(payload)
  }
}

export function DashboardPage() {
  const { data, error, loading, reload } = useAsync(loadSnapshot, [])
  const { status, subscribe } = useRealtime()
  const [events, setEvents] = useState<string[]>([])
  const degraded = status !== 'CONNECTED'

  useEffect(() => {
    const unsubscribe = subscribe('/topic/dashboard/summary', (payload) => {
      setEvents((prev) => [toStringified(payload), ...prev].slice(0, 8))
    })
    return unsubscribe
  }, [subscribe])

  return (
    <div className="page">
      <div className="page-intro">
        <Kicker>Dashboard</Kicker>
        <h1>Terminal Overview</h1>
      </div>

      {degraded ? <DegradedBanner /> : null}

      {loading ? (
        <Spinner label="Loading snapshot" />
      ) : error ? (
        <ErrorPanel error={error} onRetry={reload} />
      ) : data ? (
        <>
          <section className="kpis" aria-label="Terminal key metrics">
            <div className="kpi">
              <span className="kpi-label">Containers</span>
              <span className="kpi-value">{formatNumber(data.containerCount)}</span>
              <span className="kpi-hint">registered in terminal</span>
            </div>
            <div className="kpi">
              <span className="kpi-label">Vessels</span>
              <span className="kpi-value">{formatNumber(data.vesselCount)}</span>
              <span className="kpi-hint">on terminal roster</span>
            </div>
            <div className="kpi">
              <span className="kpi-label">Appointments</span>
              <span className="kpi-value">{formatNumber(data.appointmentCount)}</span>
              <span className="kpi-hint">booked at gate</span>
            </div>
            <div className="kpi">
              <span className="kpi-label">Yard utilization</span>
              <span className="kpi-value">{formatPercent(data.utilizationRate)}</span>
              <span className="kpi-hint">
                {formatNumber(data.movesPerHour)} moves/h — measured{' '}
                {data.measuredAt ? formatDateTime(data.measuredAt) : '—'}
              </span>
            </div>
          </section>

          <section className="grid-2">
            <div className="panel">
              <div className="panel-header">
                <h2 className="panel-title">Live activity</h2>
              </div>
              {events.length === 0 ? (
                <EmptyState
                  title="No live events yet"
                  hint={
                    degraded
                      ? 'Realtime feed unavailable — events appear here when the gateway connection returns.'
                      : 'Waiting for broadcasts on /topic/dashboard/summary.'
                  }
                />
              ) : (
                <ul className="vstack">
                  {events.map((event, index) => (
                    <li
                      key={`${index}-${event}`}
                      className="mono"
                      style={{ fontSize: 12, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}
                    >
                      {event}
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div className="panel">
              <div className="panel-header">
                <h2 className="panel-title">Open operations</h2>
              </div>
              <div className="vstack">
                <Link className="btn btn-ghost" to="/containers">
                  Containers
                </Link>
                <Link className="btn btn-ghost" to="/gate">
                  Gate appointments
                </Link>
                <Link className="btn btn-ghost" to="/yard">
                  Yard status
                </Link>
              </div>
            </div>
          </section>
        </>
      ) : null}

      <div style={{ marginTop: 16 }}>
        <Button variant="ghost" size="sm" onClick={reload}>
          Refresh
        </Button>
      </div>
    </div>
  )
}