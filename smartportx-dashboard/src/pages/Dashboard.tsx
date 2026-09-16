import { useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from '../lib/authContext';
import { list as listContainers } from '../lib/containerApi';
import { yardApi } from '../lib/yardApi';
import { gateApi } from '../lib/gateApi';
import { inPortSchedules } from '../lib/vesselApi';
import { useAsync } from '../lib/hooks';
import { subscribeTopic } from '../lib/ws';
import { formatTimeOnly } from '../lib/format';
import { PageHeader, Panel, Skeleton, Badge } from '../components/ui';
import { ErrorBanner } from '../components/feedback';

interface FeedItem {
  id: string;
  time: string;
  title: string;
  msg: string;
  tone: 'ok' | 'info' | 'warn' | 'danger';
}

function describeBody(body: unknown): Omit<FeedItem, 'id' | 'time'> {
  if (typeof body === 'string') return { title: body, msg: '', tone: 'info' };
  if (body && typeof body === 'object') {
    const b = body as Record<string, unknown>;
    const sev = String(b.severity ?? b.status ?? '').toUpperCase();
    const tone: FeedItem['tone'] =
      ['CRITICAL', 'ERROR', 'FAILED', 'HELD'].includes(sev) ? 'danger'
      : ['WARN', 'WARNING', 'ALERT'].includes(sev) ? 'warn'
      : ['COMPLETED', 'ARRIVED', 'DEPARTED', 'CONFIRMED', 'CHECKED_IN'].includes(sev) ? 'ok'
      : 'info';
    return {
      title: String(b.title ?? b.eventType ?? b.message ?? sev ?? 'Event'),
      msg: String(b.message ?? b.containerNumber ?? ''),
      tone,
    };
  }
  return { title: 'Event', msg: '', tone: 'info' };
}

const ALWAYS_TOPICS = [
  '/topic/dashboard/summary',
  '/topic/containers/movements',
  '/topic/containers/status',
  '/topic/containers/alerts',
];
const YARD_TOPICS = ['/topic/yard/status', '/topic/yard/zone/*'];
const VESSEL_TOPICS = [
  '/topic/vessel/arrivals',
  '/topic/vessel/departures',
  '/topic/vessel/discharge-plans',
  '/topic/customs/clearances',
];
const GATE_TOPICS = ['/topic/gate/status', '/topic/gate/lane/*', '/topic/appointments/*'];

export default function Dashboard() {
  const { user } = useAuth();
  const role = user?.role ?? '';
  const [feed, setFeed] = useState<FeedItem[]>([]);
  const nextId = useRef(0);

  const canYard = ['ADMIN', 'TERMINAL_OP', 'YARD_MANAGER'].includes(role);
  const canGate = ['ADMIN', 'GATE_OPERATOR'].includes(role);
  const canVessels = ['ADMIN', 'TERMINAL_OP', 'READONLY'].includes(role);

  const containers = useAsync(() => listContainers({ limit: 1 }), []);
  const yard = useAsync(
    () => (canYard ? yardApi.layout(false) : Promise.resolve(null)),
    [canYard],
  );
  const gate = useAsync(() => (canGate ? gateApi.queueView() : Promise.resolve(null)), [canGate]);
  const vessels = useAsync(
    () => (canVessels ? inPortSchedules() : Promise.resolve(null)),
    [canVessels],
  );

  const topics = useMemo(() => {
    const t = [...ALWAYS_TOPICS];
    if (canYard) t.push(...YARD_TOPICS);
    if (canVessels) t.push(...VESSEL_TOPICS);
    if (canGate) t.push(...GATE_TOPICS);
    return t;
  }, [canYard, canGate, canVessels]);

  useEffect(() => {
    const subs = topics
      .map((topic) =>
        subscribeTopic(topic, role ? [role] : [], (body) => {
          const item = describeBody(body);
          setFeed((prev) =>
            [
              { id: String(nextId.current++), time: formatTimeOnly(new Date().toISOString()), ...item },
              ...prev,
            ].slice(0, 30),
          );
        }),
      )
      .filter((s): s is NonNullable<typeof s> => s !== null);

    return () => {
      subs.forEach((s) => s.unsubscribe());
    };
  }, [topics, role]);

  const stat = (
    label: string,
    value: number | null | undefined,
    unit: string,
    sub?: string,
    loading = false,
  ) => (
    <div className="stat-card">
      <div className="stat-label">{label}</div>
      <div className="stat-value">
        {loading ? <Skeleton width={44} /> : value == null ? '–' : value}
        <span className="stat-unit">{unit}</span>
      </div>
      {sub ? <div className="stat-sub">{sub}</div> : null}
    </div>
  );

  const totalContainers = containers.data?.pagination.totalCount ?? null;
  const yardBlocks = yard.data != null ? yard.data.blocks.length : null;
  const yardOcc = yard.data != null ? `${yard.data.occupiedSlots}/${yard.data.totalSlots} slots` : null;
  const gateWaiting = gate.data?.waitingTrucks ?? null;
  const gateSub = gate.data != null ? `~${gate.data.averageWaitMinutes} min avg wait` : null;
  const vesselsInPort = vessels.data?.items.length ?? null;

  return (
    <>
      <PageHeader
        title="Dashboard"
        subtitle="Live terminal overview"
        actions={
          <button className="btn btn-ghost btn-sm" type="button" onClick={containers.reload}>
            Refresh
          </button>
        }
      />

      {containers.error ? <ErrorBanner error={containers.error} onRetry={containers.reload} /> : null}

      <div className="stat-grid">
        {stat('Containers', totalContainers, 'total', undefined, containers.loading)}
        {canYard ? stat('Yard blocks', yardBlocks, 'blocks', yardOcc ?? undefined, yard.loading) : null}
        {canGate ? stat('Gate queue', gateWaiting, 'trucks', gateSub ?? undefined, gate.loading) : null}
        {canVessels ? stat('Vessels', vesselsInPort, 'in port', undefined, vessels.loading) : null}
      </div>

      <Panel
        title="Live activity"
        subtitle={feed.length > 0 ? `${feed.length} visible` : undefined}
      >
        {feed.length === 0 ? (
          <p className="muted" style={{ padding: '12px 0' }}>
            Awaiting events…
          </p>
        ) : (
          <div className="live-feed">
            {feed.map((item) => (
              <div key={item.id} className="live-feed-item">
                <span className="feed-time">{item.time}</span>
                <span className="feed-title">{item.title}</span>
                {item.msg ? <span className="feed-msg">{item.msg}</span> : null}
                <span className="grow" />
                <Badge tone={item.tone === 'danger' ? 'danger' : item.tone === 'warn' ? 'warn' : item.tone === 'ok' ? 'ok' : 'info'}>
                  {item.tone}
                </Badge>
              </div>
            ))}
          </div>
        )}
      </Panel>
    </>
  );
}