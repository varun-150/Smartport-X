// Containers module — registrated container list with filters and pagination.

import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { containerApi } from '../../api/containerApi'
import { formatDateTime, formatNumber } from '../../lib/format'
import { useAsync } from '../../lib/hooks'
import type { ContainerResponse, ContainerStatus, ContainerType, ContainersQuery } from '../../types'
import { Button, Field, Input, Pagination, Select, Table, type Column } from '../../ui/controls'
import { EmptyState, ErrorPanel, Kicker, Spinner } from '../../ui/primitives'
import { ContainerStatusCell } from '../status'

const PAGE_SIZE = 20

const STATUS_OPTIONS: Array<{ value: ContainerStatus; label: string }> = [
  { value: 'REGISTERED', label: 'Registered' },
  { value: 'AT_GATE', label: 'At gate' },
  { value: 'IN_YARD', label: 'In yard' },
  { value: 'LOADED', label: 'Loaded' },
  { value: 'DISCHARGED', label: 'Discharged' },
  { value: 'AT_GATE_OUT', label: 'At gate out' },
  { value: 'DEPARTED', label: 'Departed' },
  { value: 'HELD', label: 'Held' },
]

const TYPE_OPTIONS: Array<{ value: ContainerType; label: string }> = [
  { value: '20DC', label: '20DC' },
  { value: '40DC', label: '40DC' },
  { value: '40HC', label: '40HC' },
  { value: '45HC', label: '45HC' },
  { value: '20RF', label: '20RF' },
  { value: '40RF', label: '40RF' },
  { value: '20TK', label: '20TK' },
  { value: '40TK', label: '40TK' },
]

interface FilterDraft {
  status: ContainerStatus | ''
  type: ContainerType | ''
  owner: string
  dangerousGoods: boolean
}

const EMPTY_DRAFT: FilterDraft = { status: '', type: '', owner: '', dangerousGoods: false }

export function ContainerDetailPage() {
  const { containerId } = useParams<'containerId'>()
  const { data, error, loading, reload } = useAsync(
    () => {
      if (!containerId) return Promise.reject(new Error('Missing container id.'))
      return containerApi.get(containerId)
    },
    [containerId],
  )

  return (
    <div className="page">
      <div className="page-intro">
        <Kicker>Containers</Kicker>
        <h1>Container detail</h1>
        <Link className="btn btn-ghost btn-sm" to="/containers">Back to register</Link>
      </div>

      {loading ? (
        <Spinner label="Loading container" />
      ) : error ? (
        <ErrorPanel error={error} onRetry={reload} />
      ) : data ? (
        <ContainerDetailCard container={data} />
      ) : (
        <EmptyState title="Container not found." hint={`No record for id ${containerId}.`} />
      )}
    </div>
  )
}

function ContainerDetailCard({ container }: { container: ContainerResponse }) {
  return (
    <div className="panel">
      <div className="panel-header">
        <h2 className="panel-title">{container.containerNumber}</h2>
      </div>
      <dl className="meta">
        <dt>ISO / type</dt>
        <dd>
          {container.isoCode} · {container.type} · {container.sizeFeet}ft
        </dd>
        <dt>Owner</dt>
        <dd>{container.owner}</dd>
        <dt>Status</dt>
        <dd>
          <ContainerStatusCell status={container.status} />
        </dd>
        <dt>Weight</dt>
        <dd>{formatNumber(container.weightKg)} kg</dd>
        <dt>Location</dt>
        <dd>{container.location ?? '—'}</dd>
        <dt>DG / restricted</dt>
        <dd>
          {container.dangerousGoods ? 'DG' : '—'} / {container.restricted ? 'restricted' : '—'}
        </dd>
        <dt>Temperature</dt>
        <dd>{container.temperatureCelsius != null ? `${container.temperatureCelsius} °C` : '—'}</dd>
        <dt>Movements</dt>
        <dd>
          {formatNumber(container.movementCount)} last{' '}
          {container.lastMovementAt ? formatDateTime(container.lastMovementAt) : '—'}
        </dd>
        <dt>Registered</dt>
        <dd>{formatDateTime(container.createdAt)}</dd>
      </dl>
    </div>
  )
}

const columns: Column<ContainerResponse>[] = [
  { key: 'containerNumber', header: 'Container', accessor: (row) => <Link to={`/containers/${row.id}`} className="mono">{row.containerNumber}</Link> },
  { key: 'type', header: 'Type', accessor: (row) => <span className="mono">{row.type}</span> },
  { key: 'owner', header: 'Owner', accessor: (row) => row.owner },
  { key: 'weight', header: 'Weight', align: 'right', accessor: (row) => `${formatNumber(row.weightKg)} kg` },
  { key: 'flags', header: 'Flags', accessor: (row) => (
    <span className="mono">
      {row.dangerousGoods ? 'DG' : ''}
      {row.dangerousGoods && row.restricted ? ' · ' : ''}
      {row.restricted ? 'RST' : ''}
    </span>
  ) },
  { key: 'status', header: 'Status', accessor: (row) => <ContainerStatusCell status={row.status} /> },
  { key: 'updatedAt', header: 'Updated', accessor: (row) => formatDateTime(row.updatedAt) },
]

export function ContainersPage() {
  const [draft, setDraft] = useState<FilterDraft>(EMPTY_DRAFT)
  const [applied, setApplied] = useState<FilterDraft>(EMPTY_DRAFT)
  const [page, setPage] = useState(1)

  const query: ContainersQuery = {
    ...(applied.status ? { status: applied.status } : {}),
    ...(applied.type ? { type: applied.type } : {}),
    ...(applied.owner.trim() ? { owner: applied.owner.trim() } : {}),
    ...(applied.dangerousGoods ? { hasDangerousGoods: true } : {}),
    page,
    size: PAGE_SIZE,
  }

  const { data, error, loading, reload } = useAsync(() => containerApi.list(query), [page, applied])

  function applyFilters() {
    setPage(1)
    setApplied(draft)
  }

  function resetFilters() {
    setDraft(EMPTY_DRAFT)
    setApplied(EMPTY_DRAFT)
    setPage(1)
  }

  return (
    <div className="page">
      <div className="page-intro">
        <Kicker>Containers</Kicker>
        <h1>Container Register</h1>
      </div>

      <form
        className="filterbar"
        onSubmit={(event) => {
          event.preventDefault()
          applyFilters()
        }}
      >
        <Field label="Status">
          <Select value={draft.status} onChange={(e) => setDraft({ ...draft, status: e.target.value as ContainerStatus | '' })}>
            <option value="">All</option>
            {STATUS_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </Select>
        </Field>
        <Field label="Type">
          <Select value={draft.type} onChange={(e) => setDraft({ ...draft, type: e.target.value as ContainerType | '' })}>
            <option value="">All</option>
            {TYPE_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </Select>
        </Field>
        <Field label="Owner">
          <Input value={draft.owner} onChange={(e) => setDraft({ ...draft, owner: e.target.value })} placeholder="e.g. MAERSK" />
        </Field>
        <Field label="Dangerous goods">
          <input
            type="checkbox"
            checked={draft.dangerousGoods}
            onChange={(e) => setDraft({ ...draft, dangerousGoods: e.target.checked })}
          />
        </Field>
        <div className="spacer" />
        <Button variant="primary" type="submit">Apply</Button>
        <Button variant="ghost" type="button" onClick={resetFilters}>Reset</Button>
      </form>

      {loading ? (
        <Spinner label="Loading containers" />
      ) : error ? (
        <ErrorPanel error={error} onRetry={reload} />
      ) : data ? (
        <>
          <Table
            columns={columns}
            rows={data.data}
            rowKey={(row) => row.id}
            emptyMessage={applied === EMPTY_DRAFT ? 'No containers registered yet.' : 'No containers match these filters.'}
          />
          <Pagination
            page={data.pagination.currentPage}
            pageSize={data.pagination.pageSize}
            totalCount={data.pagination.totalCount}
            totalPages={data.pagination.totalPages}
            onPage={(next) => setPage(next)}
          />
        </>
      ) : (
        <EmptyState title="Nothing here yet." />
      )}
    </div>
  )
}