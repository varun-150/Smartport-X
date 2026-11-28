// Testimonials module — read access for all authenticated roles, writes ADMIN-only.
// Hosted by the notification service (no pagination; newest first).

import { useEffect, useState, type FormEvent } from 'react'
import { testimonialApi } from '../../api/testimonialApi'
import { useAuth } from '../../lib/auth'
import { formatDateTime } from '../../lib/format'
import { useAsync } from '../../lib/hooks'
import { getErrorMessage } from '../../lib/http'
import type { Testimonial, TestimonialRequest } from '../../types'
import { Button, Field, Input, Modal, Select, Table, type Column } from '../../ui/controls'
import { EmptyState, ErrorPanel, Kicker, Spinner } from '../../ui/primitives'

function RatingCell({ rating }: { rating: number }) {
  return (
    <span className="mono" title={`${rating} of 5`}>
      {'★'.repeat(rating)}
      {'☆'.repeat(5 - rating)}
    </span>
  )
}

interface EditorForm {
  name: string
  role: string
  message: string
  rating: number
}

const EMPTY_FORM: EditorForm = { name: '', role: '', message: '', rating: 5 }

function TestimonialEditor({
  open,
  initial,
  onClose,
  onSaved,
}: {
  open: boolean
  initial: Testimonial | null
  onClose: () => void
  onSaved: () => void
}) {
  const [form, setForm] = useState<EditorForm>(EMPTY_FORM)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) return
    setForm({
      name: initial?.name ?? '',
      role: initial?.role ?? '',
      message: initial?.message ?? '',
      rating: initial?.rating ?? 5,
    })
    setError(null)
    setBusy(false)
  }, [open, initial])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const payload: TestimonialRequest = {
        name: form.name.trim(),
        ...(form.role.trim() ? { role: form.role.trim() } : {}),
        message: form.message.trim(),
        rating: form.rating,
      }
      if (initial) {
        await testimonialApi.update(initial.id, payload)
      } else {
        await testimonialApi.create(payload)
      }
      onSaved()
    } catch (err) {
      setError(getErrorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Modal
      open={open}
      title={initial ? 'Edit testimonial' : 'Add testimonial'}
      onClose={onClose}
      footer={
        <div className="hstack">
          <Button variant="primary" type="submit" form="testimonial-form" disabled={busy}>
            {busy ? 'Saving…' : initial ? 'Save changes' : 'Add testimonial'}
          </Button>
          <Button variant="ghost" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
        </div>
      }
    >
      <form id="testimonial-form" className="vstack" onSubmit={submit}>
        <Field label="Name" hint="Required, up to 120 characters.">
          <Input
            value={form.name}
            maxLength={120}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            required
            autoFocus
          />
        </Field>
        <Field label="Role" hint="Optional, up to 80 characters — e.g. Operations manager.">
          <Input
            value={form.role}
            maxLength={80}
            onChange={(e) => setForm({ ...form, role: e.target.value })}
          />
        </Field>
        <Field label="Message" hint="Required, up to 1000 characters.">
          <textarea
            className="input"
            value={form.message}
            maxLength={1000}
            rows={4}
            onChange={(e) => setForm({ ...form, message: e.target.value })}
            required
          />
        </Field>
        <Field label="Rating" hint="Between 1 and 5 stars.">
          <Select
            value={form.rating}
            onChange={(e) => setForm({ ...form, rating: Number(e.target.value) })}
          >
            {[1, 2, 3, 4, 5].map((n) => (
              <option key={n} value={n}>
                {n} star{n === 1 ? '' : 's'}
              </option>
            ))}
          </Select>
        </Field>
        {error ? <span className="field-error" role="alert">{error}</span> : null}
      </form>
    </Modal>
  )
}

export function TestimonialsPage() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const [editorOpen, setEditorOpen] = useState(false)
  const [editing, setEditing] = useState<Testimonial | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

  const { data, error, loading, reload } = useAsync(() => testimonialApi.list(), [reloadToken])

  function onSaved() {
    setEditorOpen(false)
    setEditing(null)
    setReloadToken((n) => n + 1)
  }

  async function remove(testimonial: Testimonial) {
    if (!window.confirm(`Delete the testimonial from ${testimonial.name}? This cannot be undone.`)) return
    try {
      await testimonialApi.remove(testimonial.id)
      setReloadToken((n) => n + 1)
    } catch {
      setReloadToken((n) => n + 1)
    }
  }

  const columns: Column<Testimonial>[] = [
    {
      key: 'author',
      header: 'Author',
      accessor: (row) => (
        <div>
          <div>{row.name}</div>
          {row.role ? (
            <div className="mono" style={{ fontSize: 11, color: 'var(--color-ink-3)' }}>{row.role}</div>
          ) : null}
        </div>
      ),
    },
    { key: 'rating', header: 'Rating', accessor: (row) => <RatingCell rating={row.rating} /> },
    { key: 'message', header: 'Message', accessor: (row) => row.message },
    {
      key: 'added',
      header: 'Added',
      accessor: (row) => (
        <span className="mono" style={{ fontSize: 12, color: 'var(--color-ink-2)' }}>
          {formatDateTime(row.createdAt)}
        </span>
      ),
    },
    ...(isAdmin
      ? [
          {
            key: 'actions',
            header: '',
            accessor: (row: Testimonial) => (
              <div className="hstack">
                <Button
                  size="sm"
                  variant="ghost"
                  type="button"
                  onClick={() => {
                    setEditing(row)
                    setEditorOpen(true)
                  }}
                >
                  Edit
                </Button>
                <Button size="sm" variant="danger" type="button" onClick={() => remove(row)}>
                  Delete
                </Button>
              </div>
            ),
          },
        ]
      : []),
  ]

  return (
    <div className="page">
      <div className="page-intro">
        <Kicker>Testimonials</Kicker>
        <h1>Customer testimonials</h1>
        {isAdmin ? (
          <Button
            variant="ghost"
            size="sm"
            type="button"
            onClick={() => {
              setEditing(null)
              setEditorOpen(true)
            }}
          >
            Add testimonial
          </Button>
        ) : null}
      </div>

      {loading ? (
        <Spinner label="Loading testimonials" />
      ) : error ? (
        <ErrorPanel error={error} onRetry={reload} />
      ) : data ? (
        <Table
          columns={columns}
          rows={data}
          rowKey={(row) => row.id}
          emptyMessage="No testimonials yet."
        />
      ) : (
        <EmptyState title="Nothing here yet." />
      )}

      <TestimonialEditor
        open={editorOpen}
        initial={editing}
        onClose={() => setEditorOpen(false)}
        onSaved={onSaved}
      />
    </div>
  )
}