// Notifications module — preference panel plus a live WebSocket feed of
// notification-service events. No REST read endpoint exists, so recent items
// come exclusively from the realtime channel.

import { useCallback, useEffect, useState } from 'react'
import { notificationApi } from '../../api/notificationApi'
import { useAuth } from '../../lib/auth'
import { formatDateTime } from '../../lib/format'
import { useAsync } from '../../lib/hooks'
import { getErrorMessage } from '../../lib/http'
import { useRealtime } from '../../lib/realtime'
import type {
  NotificationCategory,
  NotificationPayload,
  NotificationPreferences,
  DigestFrequency,
} from '../../types'
import { Button, Field, Input, Select } from '../../ui/controls'
import { DegradedBanner, EmptyState, ErrorPanel, Kicker, Spinner } from '../../ui/primitives'
import { Badge, SeverityCell } from '../status'

const CATEGORY_LABEL: Record<NotificationCategory, string> = {
  YARD_UPDATE: 'Yard updates',
  GATE_EVENT: 'Gate events',
  VESSEL_UPDATE: 'Vessel updates',
  CONTAINER_EVENT: 'Container events',
  APPOINTMENT: 'Appointments',
  ALERT: 'Alerts',
  CUSTOMS: 'Customs',
  IOT: 'IoT telemetry',
}

const DIGEST_LABEL: Record<DigestFrequency, string> = {
  DAILY: 'Daily',
  WEEKLY: 'Weekly',
}

const MAX_FEED = 20

export function NotificationsPage() {
  const { status } = useRealtime()
  const { data, error, loading, reload } = useAsync(() => notificationApi.preferences(), [])

  return (
    <div className="page">
      <div className="page-intro">
        <Kicker>Notifications</Kicker>
        <h1>Delivery preferences & live feed</h1>
      </div>

      {status !== 'CONNECTED' ? (
        <DegradedBanner
          text={`Realtime connection ${status.toLowerCase()} — preferences still load, but the live feed stays offline until the broker reconnects.`}
        />
      ) : null}

      {loading ? (
        <Spinner label="Loading preferences" />
      ) : error ? (
        <ErrorPanel error={error} onRetry={reload} />
      ) : data ? (
        <div className="grid-2">
          <PreferencesPanel preferences={data} key={JSON.stringify(data)} />
          <LiveFeed status={status} />
        </div>
      ) : (
        <EmptyState title="Nothing here yet." />
      )}
    </div>
  )
}

function PreferencesPanel({ preferences }: { preferences: NotificationPreferences }) {
  const [draft, setDraft] = useState<NotificationPreferences>(preferences)
  const [saving, setSaving] = useState(false)
  const [savedAt, setSavedAt] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function save() {
    setSaving(true)
    setError(null)
    try {
      await notificationApi.updatePreferences({
        emailEnabled: draft.emailEnabled,
        smsEnabled: draft.smsEnabled,
        digestFrequency: draft.digestFrequency,
        quietHours: draft.quietHours,
        categories: draft.categories,
      })
      setSavedAt(Date.now())
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="panel">
      <div className="panel-header">
        <h2 className="panel-title">Channels</h2>
      </div>

      <div className="vstack">
        <div className="hstack">
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
            <input
              type="checkbox"
              checked={draft.emailEnabled}
              onChange={(e) => setDraft({ ...draft, emailEnabled: e.target.checked })}
            />
            <span>Email</span>
          </label>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
            <input
              type="checkbox"
              checked={draft.smsEnabled}
              onChange={(e) => setDraft({ ...draft, smsEnabled: e.target.checked })}
            />
            <span>SMS</span>
          </label>
        </div>

        <Field label="Digest frequency" hint="Summary cadence sent over enabled channels.">
          <Select
            value={draft.digestFrequency}
            onChange={(e) =>
              setDraft({ ...draft, digestFrequency: e.target.value as DigestFrequency })
            }
          >
            {(Object.keys(DIGEST_LABEL) as DigestFrequency[]).map((key) => (
              <option key={key} value={key}>{DIGEST_LABEL[key]}</option>
            ))}
          </Select>
        </Field>

        <div className="grid-2">
          <Field label="Quiet hours from">
            <Input
              type="time"
              value={draft.quietHours.start}
              onChange={(e) =>
                setDraft({
                  ...draft,
                  quietHours: { ...draft.quietHours, start: e.target.value },
                })
              }
            />
          </Field>
          <Field label="Quiet hours to">
            <Input
              type="time"
              value={draft.quietHours.end}
              onChange={(e) =>
                setDraft({
                  ...draft,
                  quietHours: { ...draft.quietHours, end: e.target.value },
                })
              }
            />
          </Field>
        </div>

        <div className="panel-header">
          <h3 className="panel-title">Categories</h3>
        </div>
        <div className="vstack">
          {Object.entries(CATEGORY_LABEL).map(([key, label]) => (
            <label key={key} style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={Boolean(draft.categories[key as NotificationCategory])}
                onChange={(e) =>
                  setDraft({
                    ...draft,
                    categories: {
                      ...draft.categories,
                      [key]: e.target.checked,
                    },
                  })
                }
              />
              <span>{label}</span>
            </label>
          ))}
        </div>

        {error ? <span className="field-error" role="alert">{error}</span> : null}
        {savedAt ? <span style={{ fontSize: 12, color: 'var(--color-ink-3)' }}>Saved at {formatDateTime(new Date(savedAt).toISOString())}</span> : null}

        <div className="hstack">
          <Button variant="primary" onClick={save} disabled={saving}>
            {saving ? 'Saving…' : 'Save preferences'}
          </Button>
        </div>
      </div>
    </div>
  )
}

function LiveFeed({ status }: { status: ReturnType<typeof useRealtime>['status'] }) {
  const { user } = useAuth()
  const { subscribe } = useRealtime()
  const [items, setItems] = useState<NotificationPayload[]>([])

  const append = useCallback((payload: NotificationPayload) => {
    setItems((prev) => [payload, ...prev].slice(0, MAX_FEED))
  }, [])

  useEffect(() => {
    if (status !== 'CONNECTED') return
    const unsubscribe = subscribe('/topic/notifications', (raw) => {
      append(raw as NotificationPayload)
    })
    return unsubscribe
  }, [status, subscribe, append])

  const sorted = [...items].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
  )

  return (
    <div className="panel">
      <div className="panel-header">
        <h2 className="panel-title">Live feed</h2>
        <span className="mono" style={{ fontSize: 12, color: 'var(--color-ink-3)' }}>
          {sorted.length} / {MAX_FEED}
        </span>
      </div>

      {status !== 'CONNECTED' ? (
        <EmptyState
          title="Feed pending."
          hint="Notifications appear once the realtime connection is established."
        />
      ) : sorted.length === 0 ? (
        <EmptyState title="No notifications yet." hint="Events will stream in as yard, gate, and vessel services publish them." />
      ) : (
        <ol className="vstack" style={{ margin: 0, paddingLeft: 0, listStyle: 'none' }}>
          {sorted.map((item) => (
            <li key={item.id} className="panel" style={{ padding: 12 }}>
              <div className="hstack" style={{ justifyContent: 'space-between' }}>
                <Badge tone={item.category === 'ALERT' ? 'accent' : 'neutral'}>
                  {CATEGORY_LABEL[item.category] ?? item.category}
                </Badge>
                <div className="hstack">
                  <SeverityCell severity={item.severity} />
                  <span className="mono" style={{ fontSize: 11, color: 'var(--color-ink-3)' }}>
                    {formatDateTime(item.createdAt)}
                  </span>
                </div>
              </div>
              <p style={{ fontWeight: 600, margin: '8px 0 0' }}>{item.title}</p>
              <p style={{ margin: '4px 0 0', fontSize: 13, color: 'var(--color-ink-2)' }}>{item.message}</p>
              <p style={{ margin: '6px 0 0', fontSize: 11, color: 'var(--color-ink-3)' }}>
                {item.userId === user?.id ? 'me' : `for ${item.userId}`}
                {item.read ? ' · read' : ' · unread'}
              </p>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}