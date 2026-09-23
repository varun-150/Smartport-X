import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { ApiError } from '../../types/api'
import { LoginPage } from './LoginPage'

const { loginMock } = vi.hoisted(() => ({ loginMock: vi.fn() }))

vi.mock('../../lib/auth', () => ({
  useAuth: () => ({ user: null, login: loginMock }),
}))

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/dashboard" element={<div>dashboard-landed</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('LoginPage', () => {
  it('submits trimmed credentials and navigates to /dashboard on success', async () => {
    loginMock.mockResolvedValueOnce(undefined)
    renderLogin()

    await userEvent.type(screen.getByLabelText('Username'), '  ops  ')
    await userEvent.type(screen.getByLabelText('Password'), 's3cret')
    await userEvent.click(screen.getByRole('button', { name: /Sign in/ }))

    await waitFor(() => expect(loginMock).toHaveBeenCalledWith({ username: 'ops', password: 's3cret' }))
    expect(await screen.findByText('dashboard-landed')).toBeInTheDocument()
  })

  it('shows a generic invalid-credentials message on a 401', async () => {
    loginMock.mockRejectedValueOnce(new ApiError(401, 'Bad credentials', 'Unauthorized'))
    renderLogin()

    await userEvent.type(screen.getByLabelText('Username'), 'ops')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: /Sign in/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password.')
  })

  it('surfaces field-level validation errors without calling login', async () => {
    renderLogin()

    await userEvent.click(screen.getByRole('button', { name: /Sign in/ }))

    expect(await screen.findByText('Username is required.')).toBeInTheDocument()
    expect(await screen.findByText('Password is required.')).toBeInTheDocument()
    expect(loginMock).not.toHaveBeenCalled()
  })
})