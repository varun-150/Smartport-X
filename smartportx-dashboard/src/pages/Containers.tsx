import { useCallback, useState, type FormEvent } from 'react';
import {
  list,
  search,
  getById,
  movements as getMovements,
  events as getEvents,
  changeStatus,
} from '../lib/containerApi';
import { useAsync } from '../lib/hooks';
import { formatDateTime, formatWeight } from '../lib/format';
import type { ContainerResponse, ContainerStatus, ContainerType } from '../types/container';
import { PageHeader, Panel, Modal, StatusBadge, Button, Skeleton } from '../components/ui';
import { Select, TextArea } from '../components/form';
import { DataTable, Pagination } from '../components/table';
import { ErrorBanner } from '../components/feedback';

const STATUS_OPTIONS: ContainerStatus[] = [
  'REGISTERED',
  'AT_GATE',
  'IN_YARD',
  'LOADED',
  'DISCHARGED',
  'AT_GATE_OUT',
  'DEPARTED',
  'HELD',
];
const TYPE_OPTIONS: ContainerType[] = ['20DC', '40DC', '40HC', '45HC', '20RF', '40RF', '20TK', '40TK'];

const STATUS_CHOICES = STATUS_OPTIONS.map((v) => ({ value: v, label: v }));
const TYPE_CHOICES = TYPE_OPTIONS.map((v) => ({ value: v, label: v }));

const toChoice = (value: string) => ({ value, label: value });

export default function Containers() {
  const [status, setStatus] = useState<ContainerStatus | undefined>();
  const [type, setType] = useState<ContainerType | undefined>();
  const [search, setSearch] = useState('');
  const [submitted, setSubmitted] = useState('');
  const [cursor, setCursor] = useState<string | undefined>();
  const [selected, setSelected] = useState<string | null>(null);

  const fetcher = useCallback(() => {
    if (submitted !== '') {
      return search({
        containerNumber: submitted,
        status: status ? [status] : undefined,
        type: type ? [type] : undefined,
        limit: 50,
      });
    }
    return list({ status, type, cursor, limit: 50 });
  }, [submitted, status, type, cursor]);

  const { data, error, loading, reload } = useAsync(fetcher, [submitted, status, type, cursor]);

  const rows = data?.data ?? null;
  const hasMore = data?.pagination.hasMore ?? false;

  const submitSearch = (e: FormEvent) => {
    e.preventDefault();
    setSubmitted(search.trim());
    setCursor(undefined);
  };

  const resetFilters = () => {
    setStatus(undefined);
    setType(undefined);
    setSearch('');
    setSubmitted('');
    setCursor(undefined);
  };

  const selectedContainer = rows?.find((c) => c.id === selected) ?? null;

  return (
    <>
      <PageHeader
        title="Containers"
        subtitle="Fleet overview and movement audit"
        actions={
          <Button variant="ghost" size="sm" onClick={reload} loading={loading}>
            Refresh
          </Button>
        }
      />

      <Panel title="Filters">
        <form className="row" style={{ gap: 10 }} onSubmit={submitSearch}>
          <input
            className="input"
            placeholder="Container number"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            aria-label="Search by container number"
          />
          <Button type="submit" size="sm" icon="search">
            Search
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={resetFilters}>
            Reset
          </Button>
        </form>
        <div className="form-grid" style={{ marginTop: 12 }}>
          <Select
            label="Status"
            value={status ?? ''}
            onChange={(e) => {
              setStatus((e.target.value || undefined) as ContainerStatus | undefined);
              setCursor(undefined);
            }}
            options={STATUS_CHOICES}
            placeholder="Any"
          />
          <Select
            label="Type"
            value={type ?? ''}
            onChange={(e) => {
              setType((e.target.value || undefined) as ContainerType | undefined);
              setCursor(undefined);
            }}
            options={TYPE_CHOICES}
            placeholder="Any"
          />
        </div>
      </Panel>

      {error ? <ErrorBanner error={error} onRetry={reload} /> : null}

      <Panel flush>
        <DataTable<ContainerResponse>
          columns={[
            {
              key: 'containerNumber',
              header: 'Number',
              render: (c) => <span className="mono">{c.containerNumber}</span>,
            },
            { key: 'containerType', header: 'Type' },
            { key: 'status', header: 'Status', render: (c) => <StatusBadge status={c.status} /> },
            { key: 'owner', header: 'Owner' },
            {
              key: 'weight',
              header: 'Weight',
              numeric: true,
              render: (c) => (c.weight != null ? formatWeight(c.weight) : '–'),
            },
            {
              key: 'currentLocation',
              header: 'Location',
              render: (c) => c.currentLocation ?? '–',
            },
            { key: 'updatedAt', header: 'Updated', render: (c) => formatDateTime(c.updatedAt) },
          ]}
          rows={rows}
          getRowId={(c) => c.id}
          onRowClick={(c) => setSelected(c.id)}
          loading={loading}
          emptyMessage="No containers match"
        />
        <Pagination
          hasMore={hasMore}
          loading={loading}
          label={data ? `${data.pagination.totalCount} total` : undefined}
          onNext={() => {
            if (data?.pagination.nextCursor) setCursor(data.pagination.nextCursor);
          }}
          onReset={resetFilters}
        />
      </Panel>

      {selected && selectedContainer ? (
        <ContainerDetailModal
          containerId={selected}
          onClose={() => setSelected(null)}
          onChanged={reload}
        />
      ) : null}
    </>
  );
}

function ContainerDetailModal({
  containerId,
  onClose,
  onChanged,
}: {
  containerId: string;
  onClose: () => void;
  onChanged: () => void;
}) {
  const detail = useAsync(() => getById(containerId), [containerId]);
  const movements = useAsync(
    () => getMovements(containerId, { limit: 20 }),
    [containerId],
  );
  const events = useAsync(() => getEvents(containerId, { limit: 20 }), [containerId]);
  const [status, setStatus] = useState<ContainerStatus | undefined>();
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [changeError, setChangeError] = useState<unknown>(null);

  const container = detail.data;

  const handleStatusChange = async () => {
    if (!container || !status) return;
    setBusy(true);
    setChangeError(null);
    try {
      await changeStatus(container.id, { status, reason: reason || undefined });
      onChanged();
      onClose();
    } catch (err) {
      setChangeError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={`Container ${container?.containerNumber ?? '…'}`} size="lg">
      {detail.error ? <ErrorBanner error={detail.error} /> : null}
      {!container && detail.loading ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <Skeleton height={16} />
          <Skeleton height={16} />
          <Skeleton height={16} />
        </div>
      ) : null}
      {container ? (
        <>
          <dl className="def-list">
            <dt>Status</dt>
            <dd><StatusBadge status={container.status} /></dd>
            <dt>Type</dt>
            <dd>{container.containerType}</dd>
            <dt>Owner</dt>
            <dd>{container.owner}</dd>
            <dt>Weight</dt>
            <dd>{container.weight != null ? formatWeight(container.weight) : '–'}</dd>
            <dt>Location</dt>
            <dd>{container.currentLocation ?? '–'}</dd>
            <dt>Dangerous</dt>
            <dd>{container.dangerousGoods ? 'Yes' : 'No'}</dd>
            <dt>B/L</dt>
            <dd>{container.billOfLading ?? '–'}</dd>
            <dt>Seal</dt>
            <dd>{container.sealNumber ?? '–'}</dd>
            <dt>Route</dt>
            <dd>
              {container.originPort ?? '?'} → {container.destinationPort ?? '?'}
            </dd>
          </dl>

          <h4 className="muted" style={{ marginTop: 16, marginBottom: 8 }}>
            Change status
          </h4>
          <div className="form-grid">
            <Select
              label="New status"
              value={status ?? ''}
              onChange={(e) => setStatus((e.target.value || undefined) as ContainerStatus | undefined)}
              options={STATUS_CHOICES}
              placeholder="Select…"
            />
            <TextArea
              label="Reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={2}
            />
          </div>
          {changeError ? <ErrorBanner error={changeError} /> : null}
          <div className="form-actions">
            <Button variant="primary" size="sm" onClick={handleStatusChange} disabled={!status} loading={busy}>
              Apply status
            </Button>
          </div>

          <h4 className="muted" style={{ marginTop: 16, marginBottom: 8 }}>
            Movements
          </h4>
          {movements.error ? <ErrorBanner error={movements.error} /> : null}
          <table className="data-table">
            <thead>
              <tr>
                <th>Type</th>
                <th>From</th>
                <th>To</th>
                <th>When</th>
                <th>Operator</th>
              </tr>
            </thead>
            <tbody>
              {(movements.data?.data ?? []).slice(0, 8).map((m) => (
                <tr key={m.movementId}>
                  <td>{toChoice(m.movementType).label}</td>
                  <td>{m.from ?? '–'}</td>
                  <td>{m.to ?? '–'}</td>
                  <td>{formatDateTime(m.timestamp)}</td>
                  <td>{m.operator ?? '–'}</td>
                </tr>
              ))}
              {!movements.loading && (movements.data?.data.length ?? 0) === 0 ? (
                <tr>
                  <td colSpan={5} className="muted">No movements</td>
                </tr>
              ) : null}
            </tbody>
          </table>

          <h4 className="muted" style={{ marginTop: 16, marginBottom: 8 }}>
            Events
          </h4>
          {events.error ? <ErrorBanner error={events.error} /> : null}
          <table className="data-table">
            <thead>
              <tr>
                <th>Type</th>
                <th>When</th>
                <th>Source</th>
              </tr>
            </thead>
            <tbody>
              {(events.data?.data ?? []).slice(0, 8).map((e) => (
                <tr key={e.eventId}>
                  <td>{toChoice(e.eventType).label}</td>
                  <td>{formatDateTime(e.timestamp)}</td>
                  <td>{e.source ?? '–'}</td>
                </tr>
              ))}
              {!events.loading && (events.data?.data.length ?? 0) === 0 ? (
                <tr>
                  <td colSpan={3} className="muted">No events</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </>
      ) : null}
    </Modal>
  );
}