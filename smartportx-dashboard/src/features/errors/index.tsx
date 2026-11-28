// Error pages — 404 and 403. Rendered by the app router for unknown routes and
// for role-gated routes the current user cannot access.

import { Link } from 'react-router-dom'

function Shell({ title, detail }: { title: string; detail: string }) {
  return (
    <main className="canvas">
      <div className="page">
        <div className="state-panel empty">
          <span className="state-icon" role="img" aria-hidden>
            ∅
          </span>
          <strong>{title}</strong>
          <span className="mono" style={{ color: 'var(--color-ink-3)', fontSize: 12 }}>
            {detail}
          </span>
          <Link className="btn" to="/dashboard">
            Back to dashboard
          </Link>
        </div>
      </div>
    </main>
  )
}

export function NotFoundPage() {
  return <Shell title="Page not found" detail="The route you requested does not exist." />
}

export function ForbiddenPage() {
  return <Shell title="Access forbidden" detail="Your role does not permit this action." />
}