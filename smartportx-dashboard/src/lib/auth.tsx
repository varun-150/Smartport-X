// Auth domain context.
//
// Restores the session from the persisted LoginResponse.user on mount (no /auth/me endpoint
// exists in the auth-service), exposes login/logout/role helpers, and wires the http layer's
// 401 handler so an expired session redirects to /login.

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi } from '../api/authApi'
import { onUnauthorized } from '../lib/http'
import { tokenStore } from '../lib/token'
import type { LoginRequest, RoleName, UserResponse } from '../types'

interface AuthContextValue {
  user: UserResponse | null
  isAuthenticated: boolean
  login: (req: LoginRequest) => Promise<UserResponse>
  logout: () => void
  hasRole: (roles: RoleName[]) => boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(() => tokenStore.readUser<UserResponse>())

  useEffect(() => {
    // If tokens were cleared elsewhere (refresh failure), drop the restored user too.
    if (!tokenStore.hasSession()) setUser(null)
    return onUnauthorized(() => setUser(null))
  }, [])

  const login = useCallback(async (req: LoginRequest): Promise<UserResponse> => {
    const res = await authApi.login(req)
    tokenStore.save(res.accessToken, res.refreshToken)
    tokenStore.saveUser(res.user)
    setUser(res.user)
    return res.user
  }, [])

  const logout = useCallback(() => {
    tokenStore.clear()
    setUser(null)
  }, [])

  const hasRole = useCallback(
    (roles: RoleName[]) => (user ? roles.includes(user.role) : false),
    [user],
  )

  const value = useMemo<AuthContextValue>(
    () => ({ user, isAuthenticated: Boolean(user && tokenStore.hasSession()), login, logout, hasRole }),
    [user, login, logout, hasRole],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within <AuthProvider>')
  return ctx
}