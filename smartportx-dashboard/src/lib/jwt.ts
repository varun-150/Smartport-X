// Safe JWT payload decoding for the runtime console.
//
// This module ONLY base64url-decodes the payload segment of an existing token for display.
// It performs NO signature verification and is NOT a security boundary — the active RBAC
// decision remains the server-issued `role` in the login response, never decoded claims.
// Only a narrow allow-list of registered claims is surfaced to the UI.

export interface DecodedJwtClaims {
  sub?: string
  username?: string
  roles?: string[]
  iat?: number
  exp?: number
  iss?: string
  aud?: string | string[]
  jti?: string
  /** Full decoded payload, kept for transparency on the console. */
  claims: Record<string, unknown>
}

function base64UrlDecode(segment: string): string {
  const base64 = segment.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
  const binary = atob(padded)
  const bytes = Uint8Array.from(binary, (ch) => ch.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}

function toString(v: unknown): string | undefined {
  return typeof v === 'string' && v.length > 0 ? v : undefined
}

function toNumber(v: unknown): number | undefined {
  return typeof v === 'number' && Number.isFinite(v) ? v : undefined
}

function toStringArray(v: unknown): string[] | undefined {
  if (Array.isArray(v)) {
    const items = v.filter((x): x is string => typeof x === 'string')
    return items.length > 0 ? items : undefined
  }
  if (typeof v === 'string' && v.length > 0) return [v]
  return undefined
}

/** Decode the registered claims of a three-part JWT. Returns null for anything else. */
export function decodeJwtPayload(token: string | null | undefined): DecodedJwtClaims | null {
  if (!token) return null
  const parts = token.split('.')
  if (parts.length !== 3) return null
  try {
    const payload = parts[1]
    if (!payload) return null
    const parsed: unknown = JSON.parse(base64UrlDecode(payload))
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) return null
    const raw = parsed as Record<string, unknown>
    return {
      sub: toString(raw.sub),
      username: toString(raw.username),
      roles: toStringArray(raw.roles),
      iat: toNumber(raw.iat),
      exp: toNumber(raw.exp),
      iss: toString(raw.iss),
      aud: typeof raw.aud === 'string' ? (toString(raw.aud) ?? undefined) : toStringArray(raw.aud),
      jti: toString(raw.jti),
      claims: raw,
    }
  } catch {
    return null
  }
}

/** Render a token fully masked — secrets never appear in the console by default. */
export function maskToken(token: string | null | undefined): string {
  if (!token) return '—'
  return '•'.repeat(Math.max(24, Math.min(72, token.length)))
}