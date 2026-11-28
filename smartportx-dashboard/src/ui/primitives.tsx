// Small presentational primitives — Kicker, Stack, Spinner, Skeleton, EmptyState,
// ErrorPanel, StatusDot, DegradedBanner.

import type { ReactNode } from 'react'
import { ApiError } from '../types/api'
import { IconAlert } from './icons'

export function Kicker({ children }: { children: ReactNode }) {
  return <span className="kicker">{children}</span>
}

export function Stack({ children }: { children: ReactNode }) {
  return <div className="vstack">{children}</div>
}

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="state-panel" role="status">
      <span className="mono" aria-hidden={false}>
        {label}…
      </span>
    </div>
  )
}

export function Skeleton({ width = '100%', height = 16 }: { width?: number | string; height?: number }) {
  return <div className="skeleton" style={{ width, height }} />
}

export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="state-panel empty">
      <span className="state-icon" role="img" aria-hidden>
        ∅
      </span>
      <strong>{title}</strong>
      {hint ? <span className="mono" style={{ color: 'var(--color-ink-3)', fontSize: 12 }}>{hint}</span> : null}
    </div>
  )
}

export function ErrorPanel({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const apiError = error instanceof ApiError ? error : null
  const title = apiError?.title ?? 'Failed to load data'
  const detail = apiError?.detail ?? (error instanceof Error ? error.message : 'Please try again.')
  const isNetwork = apiError?.isNetwork === true
  return (
    <div className="error-panel" role="alert">
      <div className="hstack" style={{ gap: 8 }}>
        <IconAlert size={16} />
        <span className="error-title">{title}</span>
      </div>
      <div className="error-detail">
        {detail}
      </div>
      {isNetwork ? (
        <div style={{ marginTop: 6, fontSize: 12, opacity: 0.8 }}>
          Backend services appear unreachable from the browser.
        </div>
      ) : null}
      {onRetry ? (
        <button type="button" className="btn btn-sm" style={{ marginTop: 10 }} onClick={onRetry}>
          Retry
        </button>
      ) : null}
    </div>
  )
}

export type StatusTone = 'ok' | 'warn' | 'danger' | 'info'

export function StatusDot({ tone = 'info', label }: { tone: StatusTone; label: string }) {
  return (
    <span className={`status-dot ${tone}`} title={label}>
      {label}
    </span>
  )
}

/** Honest degraded banner — shown when live feeds (WS/notifications) are unavailable. */
export function DegradedBanner({ text }: { text?: string }) {
  return (
    <div className="degraded-banner" role="status">
      <IconAlert size={15} />
      <span>{text ?? 'LIVE CONNECTION UNAVAILABLE — showing last known snapshot. Data updates on refresh.'}</span>
    </div>
  )
}