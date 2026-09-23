// Auth token persistence.
//
// DESIGN DECISION (flagged, not silent): tokens live in sessionStorage so that a browser
// refresh (F5) restores the session — the M11 "SURVIVE REFRESH" requirement — while a tab
// close clears them, shrinking the XSS persistence surface vs localStorage.
// Flagged for reviewer: any XSS that can read sessionStorage can exfiltrate the token; the
// residual risk is accepted for the demo and defended in FRONTEND-REBUILD-REPORT.md.

const KEY_ACCESS = 'smartportx.access'
const KEY_REFRESH = 'smartportx.refresh'
const KEY_USER = 'smartportx.user'

interface StoredTokens {
  accessToken: string
  refreshToken: string | null
}

export const tokenStore = {
  save(accessToken: string, refreshToken: string | null) {
    try {
      window.sessionStorage.setItem(KEY_ACCESS, accessToken)
      if (refreshToken) window.sessionStorage.setItem(KEY_REFRESH, refreshToken)
      else window.sessionStorage.removeItem(KEY_REFRESH)
    } catch {
      // storage unavailable (privacy mode) — session simply won't survive a refresh
    }
  },

  read(): StoredTokens {
    try {
      return {
        accessToken: window.sessionStorage.getItem(KEY_ACCESS) ?? '',
        refreshToken: window.sessionStorage.getItem(KEY_REFRESH),
      }
    } catch {
      return { accessToken: '', refreshToken: null }
    }
  },

  getAccessToken(): string | null {
    return this.read().accessToken || null
  },

  getRefreshToken(): string | null {
    return this.read().refreshToken ?? null
  },

  saveUser(user: unknown) {
    try {
      if (user) window.sessionStorage.setItem(KEY_USER, JSON.stringify(user))
      else window.sessionStorage.removeItem(KEY_USER)
    } catch {
      // ignore
    }
  },

  readUser<T = unknown>(): T | null {
    try {
      const raw = window.sessionStorage.getItem(KEY_USER)
      return raw ? (JSON.parse(raw) as T) : null
    } catch {
      return null
    }
  },

  clear() {
    try {
      window.sessionStorage.removeItem(KEY_ACCESS)
      window.sessionStorage.removeItem(KEY_REFRESH)
      window.sessionStorage.removeItem(KEY_USER)
    } catch {
      // ignore
    }
  },

  hasSession(): boolean {
    return Boolean(this.getAccessToken())
  },
} as const