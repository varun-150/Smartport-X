// Admin module — user management (list, create, edit). Role–gated to ADMIN.

import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { usersApi } from '../../api/authApi'
import { formatDateTime } from '../../lib/format'
import { useAsync } from '../../lib/hooks'
import { getErrorMessage } from '../../lib/http'
import type { RoleName, UserResponse, UserStatus } from '../../types'
import { Button, Field, Input, Pagination, Select, Table, type Column } from '../../ui/controls'
import { EmptyState, ErrorPanel, Kicker, Spinner } from '../../ui/primitives'
import { UserStatusCell } from '../status'

const PAGE_SIZE = 20

export const ROLE_LABEL: Record<RoleName, string> = {
  ADMIN: 'Admin',
  TERMINAL_OP: 'Terminal operator',
  YARD_MANAGER: 'Yard manager',
  GATE_OPERATOR: 'Gate operator',
  READONLY: 'Read only',
}

const ROLE_OPTIONS = (Object.keys(ROLE_LABEL) as RoleName[]).map((role) => ({
  value: role,
  label: ROLE_LABEL[role],
}))

export function UsersPage() {
  const [role, setRole] = useState<RoleName | ''>('')
  const [status, setStatus] = useState<UserStatus | ''>('')
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState<{ role?: RoleName; status?: UserStatus; search?: string }>({})
  const [page, setPage] = useState(1)
  const [reloadToken, setReloadToken] = useState(0)

  const { data, error, loading, reload } = useAsync(
    () => usersApi.list({ ...query, page, size: PAGE_SIZE }),
    [query, page, reloadToken],
  )

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const next: { role?: RoleName; status?: UserStatus; search?: string } = {}
    if (role) next.role = role
    if (status) next.status = status
    if (search.trim()) next.search = search.trim()
    setQuery(next)
    setPage(1)
  }

  const columns: Column<UserResponse>[] = [
    {
      key: 'username',
      header: 'Username',
      accessor: (row) => (
        <Link to={`/admin/users/${row.id}/edit`}>{row.username}</Link>
      ),
    },
    { key: 'displayName', header: 'Name', accessor: (row) => row.displayName },
    { key: 'email', header: 'Email', accessor: (row) => row.email },
    { key: 'role', header: 'Role', accessor: (row) => ROLE_LABEL[row.role] },
    { key: 'status', header: 'Status', accessor: (row) => <UserStatusCell status={row.status} /> },
    {
      key: 'lastLoginAt',
      header: 'Last login',
      accessor: (row) => (row.lastLoginAt ? formatDateTime(row.lastLoginAt) : 'never'),
    },
    {
      key: 'actions',
      header: '',
      accessor: (row) => (
        <div className="hstack">
          <Link className="btn btn-ghost btn-sm" to={`/admin/users/${row.id}/edit`}>Edit</Link>
          {row.status === 'ACTIVE' ? (
            <button
              className="btn btn-danger btn-sm"
              type="button"
              onClick={async () => {
                try {
                  await usersApi.disable(row.id)
                  setReloadToken((n) => n + 1)
                } catch {
                  // Surface via a reload that will show the error if any
                  setReloadToken((n) => n + 1)
                }
              }}
            >
              Disable
            </button>
          ) : null}
        </div>
      ),
    },
  ]

  return (
    <div className="page">
      <div className="page-intro">
        <Kicker>Administration</Kicker>
        <h1>Users</h1>
        <Link className="btn btn-ghost btn-sm" to="/admin/users/new">Create user</Link>
      </div>

      <form className="filterbar" onSubmit={applyFilters}>
        <label className="field">
          <span className="field-label">Role</span>
          <select className="select" value={role} onChange={(e) => setRole(e.target.value as RoleName | '')}>
            <option value="">All</option>
            {ROLE_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
        </label>
        <label className="field">
          <span className="field-label">Status</span>
          <select className="select" value={status} onChange={(e) => setStatus(e.target.value as UserStatus | '')}>
            <option value="">All</option>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Inactive</option>
            <option value="LOCKED">Locked</option>
          </select>
        </label>
        <label className="field">
          <span className="field-label">Search</span>
          <input className="input" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Name, email" />
        </label>
        <div className="spacer" />
        <button className="btn btn-primary" type="submit">Apply</button>
      </form>

      {loading ? (
        <Spinner label="Loading users" />
      ) : error ? (
        <ErrorPanel error={error} onRetry={reload} />
      ) : data ? (
        <>
          <Table
            columns={columns}
            rows={data.data}
            rowKey={(row) => row.id}
            emptyMessage="No users match the current filters."
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

export function CreateUserPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState<{
    username: string
    displayName: string
    email: string
    password: string
    role: RoleName
  }>({ username: '', displayName: '', email: '', password: '', role: 'READONLY' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await usersApi.create({
        username: form.username.trim(),
        password: form.password,
        role: form.role,
        ...(form.displayName.trim() ? { displayName: form.displayName.trim() } : {}),
        ...(form.email.trim() ? { email: form.email.trim() } : {}),
      })
      navigate('/admin/users')
    } catch (err) {
      setError(getErrorMessage(err))
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <div className="page-intro">
        <Kicker>Administration</Kicker>
        <h1>Create user</h1>
      </div>

      <form className="vstack" onSubmit={submit} style={{ maxWidth: 520 }}>
        <Field label="Username">
          <Input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} required />
        </Field>
        <Field label="Display name">
          <Input value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
        </Field>
        <Field label="Email">
          <Input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </Field>
        <Field label="Password" hint="Assigned on creation; the user can change it later.">
          <Input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required />
        </Field>
        <Field label="Role">
          <Select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as RoleName })}>
            {ROLE_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </Select>
        </Field>

        {error ? <span className="field-error" role="alert">{error}</span> : null}

        <div className="hstack">
          <Button variant="primary" type="submit" disabled={busy}>
            {busy ? 'Creating…' : 'Create user'}
          </Button>
          <Link className="btn btn-ghost" to="/admin/users">Back</Link>
        </div>
      </form>
    </div>
  )
}

export function EditUserPage() {
  const navigate = useNavigate()
  const { userId } = useParams<'userId'>()
  const { data, error, loading, reload } = useAsync(
    () => {
      if (!userId) return Promise.reject(new Error('Missing user id.'))
      return usersApi.get(userId)
    },
    [userId],
  )
  const [form, setForm] = useState<{ displayName: string; email: string; role: RoleName; status: UserStatus } | null>(null)
  const [busy, setBusy] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  const current = form ?? (data ? {
    displayName: data.displayName,
    email: data.email,
    role: data.role,
    status: data.status,
  } : null)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!current || !userId) return
    setBusy(true)
    setSaveError(null)
    try {
      await usersApi.update(userId, {
        displayName: current.displayName.trim(),
        email: current.email.trim(),
        role: current.role,
        status: current.status,
      })
      navigate('/admin/users')
    } catch (err) {
      setSaveError(getErrorMessage(err))
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <div className="page-intro">
        <Kicker>Administration</Kicker>
        <h1>Edit user</h1>
      </div>

      {loading ? (
        <Spinner label="Loading user" />
      ) : error ? (
        <ErrorPanel error={error} onRetry={reload} />
      ) : current ? (
        <form className="vstack" onSubmit={submit} style={{ maxWidth: 520 }}>
          <p className="mono" style={{ fontSize: 12, color: 'var(--color-ink-3)' }}>
            @{data?.username}
          </p>
          <Field label="Display name">
            <Input
              value={current.displayName}
              onChange={(e) => setForm({ ...current, displayName: e.target.value })}
            />
          </Field>
          <Field label="Email">
            <Input
              type="email"
              value={current.email}
              onChange={(e) => setForm({ ...current, email: e.target.value })}
            />
          </Field>
          <Field label="Role">
            <Select
              value={current.role}
              onChange={(e) => setForm({ ...current, role: e.target.value as RoleName })}
            >
              {ROLE_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </Select>
          </Field>
          <Field label="Status">
            <Select
              value={current.status}
              onChange={(e) => setForm({ ...current, status: e.target.value as UserStatus })}
            >
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
              <option value="LOCKED">Locked</option>
            </Select>
          </Field>

          {saveError ? <span className="field-error" role="alert">{saveError}</span> : null}

          <div className="hstack">
            <Button variant="primary" type="submit" disabled={busy || !form}>
              {busy ? 'Saving…' : 'Save changes'}
            </Button>
            <Link className="btn btn-ghost" to="/admin/users">Back</Link>
          </div>
        </form>
      ) : (
        <EmptyState title="User not found." hint={`No record for id ${userId}.`} />
      )}
    </div>
  )
}