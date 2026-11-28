// Vessels module — fleet register and per-vessel schedule / discharge overview.

import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { vesselApi } from '../../api/vesselApi'
import { formatDateTime, formatTeu } from '../../lib/format'
import { useAsync } from '../../lib/hooks'
import type { VesselStatus, VesselType } from '../../types'
import { Table, type Column } from '../../ui/controls'
import { EmptyState, ErrorPanel, Kicker, Spinner } from '../../ui/primitives'
import { DischargeStatusCell, ScheduleStatusCell, VesselStatusCell } from '../status'
import type { VesselSchedule as VesselScheduleRow, DischargePlanContainer, VesselResponse } from '../../types'

const VESSEL_TYPE_LABEL: Record<VesselType, string> = {
  CONTAINER: 'Container',
  BULK: 'Bulk',
  TANKER: 'Tanker',
  GENERAL: 'General',
  ROLL_ON_ROLL_OFF: 'RoRo',
}

const STATUS_OPTIONS: Array<{ value: VesselStatus; label: string }> = [
  { value: 'SCHEDULED', label: 'Scheduled' },
  { value: 'IN_TRANSIT', label: 'In transit' },
  { value: 'ARRIVED', label: 'Arrived' },
  { value: 'WORKING', label: 'Working' },
  { value: 'DEPARTED', label: 'Departed' },
  { value: 'CANCELLED', label: 'Cancelled' },
]

const scheduleColumns: Column<VesselScheduleRow>[] = [
  { key: 'voyageNumber', header: 'Voyage', accessor: (row) => <span className="mono">{row.voyageNumber}</span> },
  { key: 'berthNumber', header: 'Berth', accessor: (row) => <span className="mono">{row.berthNumber}</span> },
  { key: 'eta', header: 'ETA', accessor: (row) => formatDateTime(row.eta) },
  { key: 'etd', header: 'ETD', accessor: (row) => formatDateTime(row.etd) },
  { key: 'status', header: 'Status', accessor: (row) => <ScheduleStatusCell status={row.status} /> },
]

const dischargeColumns: Column<DischargePlanContainer>[] = [
  { key: 'containerNumber', header: 'Container', accessor: (row) => <span className="mono">{row.containerNumber}</span> },
  { key: 'status', header: 'Status', accessor: (row) => <DischargeStatusCell status={row.status} /> },
  { key: 'targetBlock', header: 'Target block', accessor: (row) => row.targetBlockCode ?? '—' },
  { key: 'stackedAt', header: 'Stacked', accessor: (row) => (row.stackedAt ? formatDateTime(row.stackedAt) : '—') },
]

export function VesselsPage() {
  const [status, setStatus] = useState<VesselStatus | ''>('')
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState<{ status?: VesselStatus; search?: string }>({})

  const { data, error, loading, reload } = useAsync(() => vesselApi.list(query), [query])

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const next: { status?: VesselStatus; search?: string } = {}
    if (status) next.status = status
    if (search.trim()) next.search = search.trim()
    setQuery(next)
  }

  const columns: Column<VesselResponse>[] = [
    {
      key: 'vesselName',
      header: 'Vessel',
      accessor: (row) => (
        <Link to={`/vessels/${row.id}`}>{row.vesselName}</Link>
      ),
    },
    { key: 'imoNumber', header: 'IMO', accessor: (row) => <span className="mono">{row.imoNumber}</span> },
    {
      key: 'vesselType',
      header: 'Type',
      accessor: (row) => VESSEL_TYPE_LABEL[row.vesselType],
    },
    { key: 'capacityTeu', header: 'Capacity', align: 'right', accessor: (row) => formatTeu(row.capacityTeu) },
    { key: 'status', header: 'Status', accessor: (row) => <VesselStatusCell status={row.currentStatus} /> },
  ]

  return (
    <div className="page">
      <div className="page-intro">
        <Kicker>Fleet</Kicker>
        <h1>Vessels</h1>
      </div>

      <form className="filterbar" onSubmit={applyFilters}>
        <label className="field">
          <span className="field-label">Status</span>
          <select className="select" value={status} onChange={(e) => setStatus(e.target.value as VesselStatus | '')}>
            <option value="">All</option>
            {STATUS_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
        </label>
        <label className="field">
          <span className="field-label">Search</span>
          <input
            className="input"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Name or IMO"
          />
        </label>
        <div className="spacer" />
        <button className="btn btn-primary" type="submit">Apply</button>
      </form>

      {loading ? (
        <Spinner label="Loading vessels" />
      ) : error ? (
        <ErrorPanel error={error} onRetry={reload} />
      ) : data ? (
        <Table
          columns={columns}
          rows={data.items}
          rowKey={(row) => row.id}
          emptyMessage="No vessels match the current filters."
        />
      ) : (
        <EmptyState title="Nothing here yet." />
      )}
    </div>
  )
}

export function VesselDetailPage() {
  const { vesselId } = useParams<'vesselId'>()
  const { data, error, loading, reload } = useAsync(
    () => {
      if (!vesselId) return Promise.reject(new Error('Missing vessel id.'))
      return vesselApi.get(vesselId)
    },
    [vesselId],
  )

  return (
    <div className="page">
      <div className="page-intro">
        <Kicker>Fleet</Kicker>
        <h1>Vessel detail</h1>
        <Link className="btn btn-ghost btn-sm" to="/vessels">Back to fleet</Link>
      </div>

      {loading ? (
        <Spinner label="Loading vessel" />
      ) : error ? (
        <ErrorPanel error={error} onRetry={reload} />
      ) : data ? (
        <div className="vstack">
          <div className="panel">
            <div className="panel-header">
              <h2 className="panel-title">{data.vessel.vesselName}</h2>
              <span className="mono" style={{ fontSize: 12, color: 'var(--color-ink-3)' }}>
                {data.vessel.imoNumber}
              </span>
            </div>
            <dl className="meta">
              <dt>Type</dt>
              <dd>{VESSEL_TYPE_LABEL[data.vessel.vesselType]}</dd>
              <dt>Capacity</dt>
              <dd>{formatTeu(data.vessel.capacityTeu)}</dd>
              <dt>Status</dt>
              <dd>
                <VesselStatusCell status={data.vessel.currentStatus} />
              </dd>
              <dt>Registered</dt>
              <dd>{formatDateTime(data.vessel.registeredAt)}</dd>
            </dl>
          </div>

          <section>
            <div className="panel-header">
              <h2 className="panel-title">Schedules</h2>
            </div>
            <Table
              columns={scheduleColumns}
              rows={data.schedules}
              rowKey={(row) => row.id}
              emptyMessage="No schedules recorded for this vessel."
            />
          </section>

          <section>
            <div className="panel-header">
              <h2 className="panel-title">Discharge plan</h2>
            </div>
            <Table
              columns={dischargeColumns}
              rows={data.dischargePlans}
              rowKey={(row) => row.id}
              emptyMessage="No discharge plan recorded for this vessel."
            />
          </section>
        </div>
      ) : (
        <EmptyState title="Vessel not found." hint={`No record for id ${vesselId}.`} />
      )}
    </div>
  )
}