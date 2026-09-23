// Gate module — appointment queue (confirm / cancel) and appointment booking.

import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { gateApi } from '../../api/gateApi'
import { useAuth } from '../../lib/auth'
import { formatDateTime } from '../../lib/format'
import { useAsync } from '../../lib/hooks'
import { getErrorMessage } from '../../lib/http'
import type { AppointmentResponse, AppointmentStatus, AppointmentType, AppointmentQuery } from '../../types'
import { Button, Field, Input, Modal, Pagination, Select, Table, type Column } from '../../ui/controls'
import { EmptyState, ErrorPanel, Kicker, Spinner } from '../../ui/primitives'
import { AppointmentStatusCell } from '../status'

const PAGE_SIZE = 20

const STATUS_OPTIONS: Array<{ value: AppointmentStatus; label: string }> = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'CHECKED_IN', label: 'Checked in' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'NO_SHOW', label: 'No show' },
]

const TYPE_OPTIONS: Array<{ value: AppointmentType; label: string }> = [
  { value: 'PICKUP', label: 'Pickup' },
  { value: 'DROP_OFF', label: 'Drop off' },
  { value: 'BOTH', label: 'Both' },
]

interface ActionState {
  appointment: AppointmentResponse
  confirmCode: string
  reason: string
  busy: boolean
  error: string | null
}

export function GatePage() {
  const { user } = useAuth()
  const [status, setStatus] = useState<AppointmentStatus | ''>('')
  const [containerNumber, setContainerNumber] = useState('')
  const [query, setQuery] = useState<AppointmentQuery>({})
  const [page, setPage] = useState(1)
  const [action, setAction] = useState<ActionState | null>(null)
  const [actionMode, setActionMode] = useState<'confirm' | 'cancel'>('confirm')
  const [saved, setSaved] = useState(0)

  const { data, error, loading, reload } = useAsync(
    () => gateApi.list({ ...query, page, size: PAGE_SIZE }),
    [query, page, saved],
  )

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const next: AppointmentQuery = {}
    if (status) next.status = status
    if (containerNumber.trim()) next.containerNumber = containerNumber.trim()
    setQuery(next)
    setPage(1)
  }

  async function runAction() {
    if (!action) return
    const appointment = action.appointment
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    setAction((prev) => (prev ? { ...prev, busy: true, error: null } : prev))
    try {
      if (actionMode === 'confirm') {
        await gateApi.confirm(appointment.id, {
          confirmationCode: action.confirmCode.trim(),
          verifiedBy: user?.username ?? '',
        })
      } else {
        await gateApi.cancel(appointment.id, {
          reason: action.reason.trim() || 'No reason provided',
          cancelledBy: user?.username ?? '',
        })
      }
      setAction(null)
      setSaved((n) => n + 1)
    } catch (err) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      setAction((prev) => (prev ? { ...prev, busy: false, error: getErrorMessage(err) } : prev))
    }
  }

  const columns: Column<AppointmentResponse>[] = [
    { key: 'appointmentNumber', header: 'Appointment', accessor: (row) => <span className="mono">{row.appointmentNumber}</span> },
    { key: 'containerNumber', header: 'Container', accessor: (row) => <span className="mono">{row.containerNumber}</span> },
    { key: 'type', header: 'Type', accessor: (row) => TYPE_OPTIONS.find((o) => o.value === row.appointmentType)?.label ?? row.appointmentType },
    { key: 'scheduledFor', header: 'Scheduled', accessor: (row) => formatDateTime(row.scheduledFor) },
    { key: 'status', header: 'Status', accessor: (row) => <AppointmentStatusCell status={row.status} /> },
    {
      key: 'actions',
      header: 'Actions',
      accessor: (row) =>
        row.status === 'PENDING' || row.status === 'CONFIRMED' ? (
          <div className="hstack">
            {row.status === 'PENDING' ? (
              <Button
                size="sm"
                variant="ghost"
                onClick={() => {
                  setAction({
                    appointment: row,
                    confirmCode: '',
                    reason: '',
                    busy: false,
                    error: null,
                  })
                  setActionMode('confirm')
                }}
              >
                Confirm
              </Button>
            ) : null}
            <Button
              size="sm"
              variant="ghost"
              onClick={() => {
                setAction({
                  appointment: row,
                  confirmCode: '',
                  reason: '',
                  busy: false,
                  error: null,
                })
                setActionMode('cancel')
              }}
            >
              Cancel
            </Button>
          </div>
        ) : null,
    },
  ]

  return (
    <div className="page">
      <div className="page-intro">
        <Kicker>Gate</Kicker>
        <h1>Appointments</h1>
        <Link className="btn btn-ghost btn-sm" to="/gate/book">Book appointment</Link>
      </div>

      <form className="filterbar" onSubmit={applyFilters}>
        <Field label="Status">
          <Select value={status} onChange={(e) => setStatus(e.target.value as AppointmentStatus | '')}>
            <option value="">All</option>
            {STATUS_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </Select>
        </Field>
        <Field label="Container">
          <Input value={containerNumber} onChange={(e) => setContainerNumber(e.target.value)} placeholder="e.g. MSKU1234567" />
        </Field>
        <div className="spacer" />
        <Button variant="primary" type="submit">Apply</Button>
      </form>

      {loading ? (
        <Spinner label="Loading appointments" />
      ) : error ? (
        <ErrorPanel error={error} onRetry={reload} />
      ) : data ? (
        <>
          <Table
            columns={columns}
            rows={data.data}
            rowKey={(row) => row.id}
            emptyMessage="No appointments match the current filters."
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

      <Modal
        open={action != null}
        title={actionMode === 'confirm' ? 'Confirm appointment' : 'Cancel appointment'}
        onClose={() => setAction(null)}
        footer={
          action ? (
            <div className="hstack" style={{ justifyContent: 'flex-end' }}>
              <Button variant="ghost" onClick={() => setAction(null)}>Close</Button>
              <Button variant={actionMode === 'confirm' ? 'primary' : 'danger'} disabled={action.busy} onClick={runAction}>
                {actionMode === 'confirm' ? 'Confirm' : 'Cancel appointment'}
              </Button>
            </div>
          ) : null
        }
      >
        {action ? (
          <div className="vstack">
            <p style={{ fontSize: 14, color: 'var(--color-ink-2)' }}>
              <span className="mono">{action.appointment.appointmentNumber}</span> ·{' '}
              <span className="mono">{action.appointment.containerNumber}</span>
            </p>
            {actionMode === 'confirm' ? (
              <Field label="Confirmation code" hint="Code issued to the trucker at the gate.">
                <Input
                  value={action.confirmCode}
                  onChange={(e) => setAction({ ...action, confirmCode: e.target.value })}
                />
              </Field>
            ) : (
              <Field label="Reason">
                <Input
                  value={action.reason}
                  onChange={(e) => setAction({ ...action, reason: e.target.value })}
                  placeholder="Trucker no-show, reschedule…"
                />
              </Field>
            )}
            {action.error ? (
              <span className="field-error" role="alert">{action.error}</span>
            ) : null}
          </div>
        ) : null}
      </Modal>
    </div>
  )
}

interface BookDraft {
  containerNumber: string
  appointmentType: AppointmentType
  scheduledFor: string
  laneId: string
  truckCompany: string
  plateNumber: string
  driverName: string
}

const EMPTY_BOOK: BookDraft = {
  containerNumber: '',
  appointmentType: 'PICKUP',
  scheduledFor: '',
  laneId: '',
  truckCompany: '',
  plateNumber: '',
  driverName: '',
}

export function BookAppointmentPage() {
  const navigate = useNavigate()
  const [draft, setDraft] = useState<BookDraft>(EMPTY_BOOK)
  const [preview, setPreview] = useState<{ eligible: boolean; availableSlots: number; message: string | null; earliestSlot: string | null } | null>(null)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [previewError, setPreviewError] = useState<string | null>(null)
  const [bookError, setBookError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function handlePreview() {
    setPreviewLoading(true)
    setPreviewError(null)
    try {
      const result = await gateApi.preview({
        containerNumber: draft.containerNumber.trim(),
        appointmentType: draft.appointmentType,
      })
      setPreview(result)
    } catch (err) {
      setPreviewError(getErrorMessage(err))
      setPreview(null)
    } finally {
      setPreviewLoading(false)
    }
  }

  async function handleBook(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setBookError(null)
    try {
      const result = await gateApi.book({
        containerNumber: draft.containerNumber.trim(),
        appointmentType: draft.appointmentType,
        scheduledFor: draft.scheduledFor ? new Date(draft.scheduledFor).toISOString() : new Date().toISOString(),
        ...(draft.laneId.trim() ? { laneId: draft.laneId.trim() } : {}),
        ...(draft.truckCompany.trim() ? { truckCompany: draft.truckCompany.trim() } : {}),
        ...(draft.plateNumber.trim() ? { plateNumber: draft.plateNumber.trim() } : {}),
        ...(draft.driverName.trim() ? { driverName: draft.driverName.trim() } : {}),
      })
      navigate(`/gate`, { state: { booked: result.appointmentNumber } })
    } catch (err) {
      setBookError(getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <div className="page-intro">
        <Kicker>Gate</Kicker>
        <h1>Book appointment</h1>
      </div>

      <form className="vstack" onSubmit={handleBook} style={{ maxWidth: 520 }}>
        <Field label="Container number">
          <Input
            value={draft.containerNumber}
            onChange={(e) => setDraft({ ...draft, containerNumber: e.target.value })}
            required
          />
        </Field>

        <Field label="Operation">
          <Select
            value={draft.appointmentType}
            onChange={(e) => setDraft({ ...draft, appointmentType: e.target.value as AppointmentType })}
          >
            {TYPE_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </Select>
        </Field>

        <Field label="Scheduled for" hint="Local time — converted to UTC before submission.">
          <Input
            type="datetime-local"
            value={draft.scheduledFor}
            onChange={(e) => setDraft({ ...draft, scheduledFor: e.target.value })}
          />
        </Field>

        <Field label="Lane">
          <Input value={draft.laneId} onChange={(e) => setDraft({ ...draft, laneId: e.target.value })} placeholder="optional" />
        </Field>

        <div className="grid-2">
          <Field label="Truck company">
            <Input value={draft.truckCompany} onChange={(e) => setDraft({ ...draft, truckCompany: e.target.value })} />
          </Field>
          <Field label="Plate number">
            <Input value={draft.plateNumber} onChange={(e) => setDraft({ ...draft, plateNumber: e.target.value })} />
          </Field>
        </div>

        <Field label="Driver">
          <Input value={draft.driverName} onChange={(e) => setDraft({ ...draft, driverName: e.target.value })} />
        </Field>

        <div className="hstack">
          <Button type="button" variant="ghost" disabled={previewLoading || !draft.containerNumber.trim()} onClick={handlePreview}>
            {previewLoading ? 'Checking…' : 'Check slot availability'}
          </Button>
        </div>

        {previewError ? (
          <span className="field-error" role="alert">{previewError}</span>
        ) : preview ? (
          <div className="panel" style={{ borderLeftColor: preview.eligible ? 'var(--color-ok)' : 'var(--color-danger)', borderLeftWidth: 3, borderLeftStyle: 'solid' }}>
            <dl className="meta">
              <dt>Eligible</dt>
              <dd>{preview.eligible ? 'Yes' : 'No'}</dd>
              <dt>Available slots</dt>
              <dd>{preview.availableSlots}</dd>
              <dt>Earliest slot</dt>
              <dd>{preview.earliestSlot ? formatDateTime(preview.earliestSlot) : '—'}</dd>
              {preview.message ? (
                <>
                  <dt>Note</dt>
                  <dd>{preview.message}</dd>
                </>
              ) : null}
            </dl>
          </div>
        ) : null}

        {bookError ? (
          <span className="field-error" role="alert">{bookError}</span>
        ) : null}

        <div className="hstack">
          <Button variant="primary" type="submit" disabled={busy || !draft.containerNumber.trim()}>
            {busy ? 'Booking…' : 'Book appointment'}
          </Button>
          <Link className="btn btn-ghost" to="/gate">Back</Link>
        </div>
      </form>
    </div>
  )
}