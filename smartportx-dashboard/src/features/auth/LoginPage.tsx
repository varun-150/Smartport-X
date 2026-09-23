// Sign-in page — issues credentials via the auth service and lets the
// OAuth flow restore the session on refresh.

import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../lib/auth'
import { getErrorMessage } from '../../lib/http'
import { ApiError } from '../../types/api'
import { Button, Field, Input } from '../../ui/controls'
import { Kicker } from '../../ui/primitives'

interface LoginLocationState {
  from?: { pathname?: string }
}

const INVALID_CREDENTIALS = 'Invalid username or password.'
const REQUIRED_USERNAME = 'Username is required.'
const REQUIRED_PASSWORD = 'Password is required.'

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<{ username?: string; password?: string }>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (user) {
    return <Navigate to="/dashboard" replace />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const next: { username?: string; password?: string } = {}
    if (!username.trim()) next.username = REQUIRED_USERNAME
    if (!password) next.password = REQUIRED_PASSWORD
    if (next.username || next.password) {
      setFieldErrors(next)
      setFormError(null)
      return
    }

    setSubmitting(true)
    setFormError(null)
    try {
      await login({ username: username.trim(), password })
      const state = location.state as LoginLocationState | null
      navigate(state?.from?.pathname || '/dashboard', { replace: true })
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 401 || err.status === 422) {
          setFormError(INVALID_CREDENTIALS)
        } else if (err.fieldErrors) {
          setFieldErrors({
            username: err.fieldErrors.username,
            password: err.fieldErrors.password,
          })
        } else {
          setFormError(getErrorMessage(err))
        }
      } else {
        setFormError(getErrorMessage(err))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="canvas">
      <div className="login-card">
        <Kicker>SmartPortX</Kicker>
        <h1>Terminal Operations</h1>
        <p className="mono" style={{ color: 'var(--color-ink-3)', fontSize: 13 }}>
          Sign in with your terminal account.
        </p>

        <form className="vstack" onSubmit={handleSubmit} noValidate>
          <Field label="Username" error={fieldErrors.username}>
            <Input
              autoComplete="username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              invalid={Boolean(fieldErrors.username)}
              autoFocus
            />
          </Field>

          <Field label="Password" error={fieldErrors.password}>
            <Input
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              invalid={Boolean(fieldErrors.password)}
            />
          </Field>

          {formError ? (
            <span className="field-error" role="alert">
              {formError}
            </span>
          ) : null}

          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </Button>
        </form>
      </div>
    </main>
  )
}