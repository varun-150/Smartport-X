// Yard module — utilization metrics, per-block overview, and equipment roster.

import { useState } from 'react'
import { yardApi } from '../../api/yardApi'
import { formatNumber, formatPercent } from '../../lib/format'
import { useAsync } from '../../lib/hooks'
import type { YardEquipment } from '../../types'
import type { BlockType } from '../../types/yard'
import { Button, Table, type Column } from '../../ui/controls'
import { EmptyState, ErrorPanel, Kicker, Spinner } from '../../ui/primitives'
import { EquipmentStatusCell } from '../status'

const BLOCK_LABEL: Record<BlockType, string> = {
  EXPORT: 'Export',
  IMPORT: 'Import',
  TRANSIT: 'Transit',
  REFRIGERATED: 'Reefer',
  DANGEROUS: 'Dangerous',
  EMPTY: 'Empty',
}

function blockTypeLabel(type: BlockType | null): string {
  return type ? (BLOCK_LABEL[type] ?? 'General') : 'General'
}

export function YardPage() {
  const [period, setPeriod] = useState<'24h' | '7d' | '30d'>('24h')
  const metrics = useAsync(() => yardApi.metrics(period), [period])
  const blocks = useAsync(() => yardApi.metricsByBlock(), [])

  return (
    <div className="page">
      <div className="page-intro">
        <Kicker>Yard</Kicker>
        <h1>Stacking Operations</h1>
        <div className="hstack">
          {(['24h', '7d', '30d'] as const).map((p) => (
            <Button key={p} size="sm" variant={p === period ? 'primary' : 'ghost'} onClick={() => setPeriod(p)}>
              {p}
            </Button>
          ))}
        </div>
      </div>

      {metrics.loading ? (
        <Spinner label="Loading yard metrics" />
      ) : metrics.error ? (
        <ErrorPanel error={metrics.error} onRetry={metrics.reload} />
      ) : metrics.data ? (
        <YardMetricsCards
          totalSlots={metrics.data.totalSlots}
          occupiedSlots={metrics.data.occupiedSlots}
          utilizationRate={metrics.data.utilizationRate}
          reeferLoad={metrics.data.reeferLoad}
          dgLoad={metrics.data.dgLoad}
          movesPerHour={metrics.data.movesPerHour}
        />
      ) : null}

      <section style={{ marginTop: 24 }}>
        <div className="panel-header">
          <h2 className="panel-title">Blocks</h2>
        </div>
        {blocks.loading ? (
          <Spinner label="Loading blocks" />
        ) : blocks.error ? (
          <ErrorPanel error={blocks.error} onRetry={blocks.reload} />
        ) : blocks.data ? (
          blocks.data.length === 0 ? (
            <EmptyState title="No blocks reported." hint="The yard service returned an empty roster." />
          ) : (
            <div className="grid-3">
              {blocks.data.map((block) => (
                <div className="panel" key={block.blockCode}>
                  <div className="panel-header">
                    <h3 className="panel-title">{block.blockCode}</h3>
                    <span className="mono" style={{ fontSize: 12, color: 'var(--color-ink-3)' }}>
                      {blockTypeLabel(block.type)}
                    </span>
                  </div>
                  <dl className="meta">
                    <dt>Capacity</dt>
                    <dd>{formatNumber(block.capacity)}</dd>
                    <dt>Occupied</dt>
                    <dd>{formatNumber(block.occupied)}</dd>
                    <dt>Utilization</dt>
                    <dd>{formatPercent(block.utilizationRate)}</dd>
                    <dt>Moves / h</dt>
                    <dd>{formatNumber(block.movesPerHour)}</dd>
                  </dl>
                  <div className="bars">
                    <span className="bar" style={{ width: `${Math.min(block.utilizationRate, 100)}%` }} />
                  </div>
                </div>
              ))}
            </div>
          )
        ) : null}
      </section>
    </div>
  )
}

function YardMetricsCards(props: {
  totalSlots: number
  occupiedSlots: number
  utilizationRate: number
  reeferLoad: number
  dgLoad: number
  movesPerHour: number
}) {
  return (
    <section className="kpis" aria-label="Yard key metrics">
      <div className="kpi">
        <span className="kpi-label">Slots</span>
        <span className="kpi-value">{formatNumber(props.occupiedSlots)} / {formatNumber(props.totalSlots)}</span>
        <span className="kpi-hint">occupied</span>
      </div>
      <div className="kpi">
        <span className="kpi-label">Utilization</span>
        <span className="kpi-value">{formatPercent(props.utilizationRate)}</span>
        <span className="kpi-hint">stack occupancy</span>
      </div>
      <div className="kpi">
        <span className="kpi-label">Slow-moving cargo</span>
        <span className="kpi-value">{formatPercent(props.reeferLoad)}</span>
        <span className="kpi-hint">reefer slots in use</span>
      </div>
      <div className="kpi">
        <span className="kpi-label">Dangerous cargo</span>
        <span className="kpi-value">{formatPercent(props.dgLoad)}</span>
        <span className="kpi-hint">DG slots in use</span>
      </div>
      <div className="kpi">
        <span className="kpi-label">Throughput</span>
        <span className="kpi-value">{formatNumber(props.movesPerHour)}</span>
        <span className="kpi-hint">moves per hour</span>
      </div>
    </section>
  )
}

const equipmentColumns: Column<YardEquipment>[] = [
  { key: 'equipmentCode', header: 'Unit', accessor: (row) => <span className="mono">{row.equipmentCode}</span> },
  { key: 'type', header: 'Type', accessor: (row) => <span className="mono">{row.type}</span> },
  { key: 'status', header: 'Status', accessor: (row) => <EquipmentStatusCell status={row.status} /> },
  { key: 'block', header: 'Block', accessor: (row) => row.currentBlockCode ?? '—' },
  { key: 'operator', header: 'Operator', accessor: (row) => row.operatorId ?? '—' },
  { key: 'utilization', header: 'Utilization', align: 'right', accessor: (row) => formatPercent(row.utilizationRate) },
]

export function YardEquipmentPage() {
  const { data, error, loading, reload } = useAsync(() => yardApi.equipment(), [])
  return (
    <div className="page">
      <div className="page-intro">
        <Kicker>Yard</Kicker>
        <h1>Equipment</h1>
      </div>

      {loading ? (
        <Spinner label="Loading equipment" />
      ) : error ? (
        <ErrorPanel error={error} onRetry={reload} />
      ) : data ? (
        <Table
          columns={equipmentColumns}
          rows={data.items}
          rowKey={(row) => row.id}
          emptyMessage="No equipment reported by the yard service."
        />
      ) : (
        <EmptyState title="Nothing here yet." />
      )}
    </div>
  )
}