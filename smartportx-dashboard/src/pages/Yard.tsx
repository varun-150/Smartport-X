import { useCallback, useState } from 'react';
import { yardApi } from '../lib/yardApi';
import { useAsync } from '../lib/hooks';
import { StatusBadge, Badge } from '../components/ui';
import { PageHeader, Panel, Modal } from '../components/ui';
import { Button } from '../components/ui';
import { DataTable, Pagination } from '../components/table';
import { ErrorBanner, EmptyState } from '../components/feedback';
import { formatDateTime, formatNumber, formatPercent, titleCase } from '../lib/format';
import type { YardLayout, BlockLayout, BlockType, EquipmentResponse } from '../types/yard';

const BLOCK_TYPE_LABEL: Record<BlockType, string> = {
  IMPORT: 'Import',
  EXPORT: 'Export',
  EMPTY: 'Empty',
  REEFER: 'Reefer',
  DANGEROUS: 'Dangerous',
  MIXED: 'Mixed',
};

function blockTypeTone(t: BlockType): 'info' | 'accent' | 'warn' | 'danger' | 'ok' | 'neutral' {
  switch (t) {
    case 'IMPORT':
      return 'info';
    case 'EXPORT':
      return 'accent';
    case 'EMPTY':
      return 'neutral';
    case 'REEFER':
      return 'warn';
    case 'DANGEROUS':
      return 'danger';
    default:
      return 'ok';
  }
}

export default function YardPage() {
  const [selected, setSelected] = useState<BlockLayout | null>(null);
  const { data, error, loading, reload } = useAsync<YardLayout>(() => yardApi.layout(true), []);
  const equipment = useAsync<EquipmentResponse[]>(() => yardApi.equipment({ limit: 100 }), []);

  const blocks = data?.blocks ?? [];
  const totalSlots = data?.totalSlots ?? 0;
  const occupied = data?.occupiedSlots ?? 0;
  const util = totalSlots ? (occupied / totalSlots) * 100 : 0;

  return (
    <div className="page">
      <PageHeader
        title="Yard"
        subtitle="Blocks, bays, and utilization across the yard"
        actions={
          <Button variant="ghost" icon="refresh" onClick={reload} loading={loading}>
            Refresh
          </Button>
        }
      />

      <ErrorBanner error={error} />

      {data ? (
        <div className="stat-grid">
          <div className="stat-card">
            <div className="stat-label">Blocks</div>
            <div className="stat-value">{formatNumber(data.totalBlocks)}</div>
            <div className="stat-sub">total</div>
          </div>
          <div className="stat-card">
            <div className="stat-label">Total slots</div>
            <div className="stat-value">{formatNumber(totalSlots)}</div>
            <div className="stat-sub">across all blocks</div>
          </div>
          <div className="stat-card">
            <div className="stat-label">Occupied</div>
            <div className="stat-value">{formatNumber(occupied)}</div>
            <div className="stat-sub mono">{formatPercent(util)} utilization</div>
          </div>
          <div className="stat-card">
            <div className="stat-label">Last updated</div>
            <div className="stat-value stat-value-sm">{formatDateTime(data.lastUpdated)}</div>
          </div>
        </div>
      ) : null}

      <Panel
        title="Blocks"
        subtitle="Click a block for bay detail"
        actions={<Badge tone="accent">{formatNumber(blocks.length)} blocks</Badge>}
      >
        <DataTable<BlockLayout>
          columns={[
            { key: 'blockCode', header: 'Code', className: 'mono', width: 110 },
            { key: 'name', header: 'Name' },
            {
              key: 'type',
              header: 'Type',
              render: (b) => <Badge tone={blockTypeTone(b.type)}>{BLOCK_TYPE_LABEL[b.type]}</Badge>,
            },
            { key: 'totalSlots', header: 'Slots', numeric: true, render: (b) => formatNumber(b.totalSlots) },
            { key: 'occupiedSlots', header: 'Occupied', numeric: true, render: (b) => formatNumber(b.occupiedSlots) },
            {
              key: 'utilizationRate',
              header: 'Utilization',
              numeric: true,
              render: (b) => (
                <span className="util-bar">
                  <span className="fill" style={{ width: `${Math.min(100, Math.max(0, b.utilizationRate))}%` }} />
                </span>
              ),
            },
          ]}
          rows={blocks}
          loading={loading}
          getRowId={(b) => b.blockId}
          onRowClick={(b) => setSelected(b)}
          emptyMessage="No blocks"
        />
      </Panel>

      {equipment.data ? (
        <Panel
          title="Equipment"
          subtitle="Yard handling equipment"
          actions={<Badge tone="accent">{formatNumber(equipment.data.length)} units</Badge>}
        >
          <DataTable<EquipmentResponse>
            columns={[
              { key: 'equipmentCode', header: 'Code', className: 'mono', width: 120 },
              { key: 'equipmentType', header: 'Type', render: (e) => titleCase(e.equipmentType.replace(/_/g, ' ')) },
              { key: 'status', header: 'Status', render: (e) => <StatusBadge status={e.status} /> },
              { key: 'blockId', header: 'Assigned block', render: (e) => e.blockId ?? <span className="muted">—</span> },
            ]}
            rows={equipment.data}
            loading={equipment.loading}
            getRowId={(e) => e.equipmentId}
            emptyMessage="No equipment"
          />
        </Panel>
      ) : null}

      <Modal
        open={selected !== null}
        onClose={() => setSelected(null)}
        title={selected ? `Block ${selected.blockCode}` : ''}
        size="lg"
      >
        {selected ? (
          <div className="def-list">
            <div className="def-list-row">
              <dt>Name</dt>
              <dd>{selected.name}</dd>
            </div>
            <div className="def-list-row">
              <dt>Type</dt>
              <dd>
                <Badge tone={blockTypeTone(selected.type)}>{BLOCK_TYPE_LABEL[selected.type]}</Badge>
              </dd>
            </div>
            <div className="def-list-row">
              <dt>Utilization</dt>
              <dd>
                {formatPercent(selected.utilizationRate)}{' '}
                <span className="muted">
                  ({formatNumber(selected.occupiedSlots)}/{formatNumber(selected.totalSlots)} slots)
                </span>
              </dd>
            </div>
            <div className="def-list-row">
              <dt>Layout</dt>
              <dd>
                {formatNumber(selected.totalBays)} bays · {formatNumber(selected.totalRows)} rows ·{' '}
                {formatNumber(selected.totalTiers)} tiers
              </dd>
            </div>
            <div className="def-list-row">
              <dt>Dangerous goods</dt>
              <dd>{selected.dangerousGoods ? 'Allowed' : 'Not allowed'}</dd>
            </div>
          </div>
        ) : null}
      </Modal>
    </div>
  );
}
