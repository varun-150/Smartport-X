import { describe, expect, it } from 'vitest'
import { decodeJwtPayload, maskToken } from './jwt'

function b64url(obj: unknown): string {
  const json = JSON.stringify(obj)
  return btoa(unescape(encodeURIComponent(json))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function token(claims: unknown): string {
  return `header.${b64url(claims)}.signature`
}

describe('decodeJwtPayload', () => {
  it('decodes registered claims from a three-part JWT', () => {
    const decoded = decodeJwtPayload(
      token({ sub: 'u-1', username: 'ops', roles: ['ADMIN'], iat: 10, exp: 100, iss: 'smartportx', aud: 'spa' }),
    )
    expect(decoded).not.toBeNull()
    expect(decoded?.sub).toBe('u-1')
    expect(decoded?.username).toBe('ops')
    expect(decoded?.roles).toEqual(['ADMIN'])
    expect(decoded?.iat).toBe(10)
    expect(decoded?.exp).toBe(100)
    expect(decoded?.iss).toBe('smartportx')
    expect(decoded?.aud).toBe('spa')
  })

  it('normalises a single-string roles claim into an array', () => {
    const decoded = decodeJwtPayload(token({ roles: 'READONLY' }))
    expect(decoded?.roles).toEqual(['READONLY'])
  })

  it('keeps unknown claims accessible via raw payload', () => {
    const decoded = decodeJwtPayload(token({ custom: { nested: true } }))
    expect(decoded?.claims.custom).toEqual({ nested: true })
  })

  it('returns null for malformed tokens', () => {
    expect(decodeJwtPayload(null)).toBeNull()
    expect(decodeJwtPayload('')).toBeNull()
    expect(decodeJwtPayload('two.parts')).toBeNull()
    expect(decodeJwtPayload('a.b.c')).toBeNull()
    expect(decodeJwtPayload('a.!!!.c')).toBeNull()
  })

  it('returns null for a parseable but non-object payload', () => {
    expect(decodeJwtPayload(token('just a string'))).toBeNull()
  })
})

describe('maskToken', () => {
  it('masks token fully — never leaks real characters', () => {
    const masked = maskToken('aaaaaaaaaa.bbbbbbbbbb.cccccccccc')
    expect(masked).toContain('•')
    expect(masked).not.toContain('a')
    expect(masked).not.toContain('b')
  })

  it('returns a dash for absent tokens', () => {
    expect(maskToken(null)).toBe('—')
    expect(maskToken('')).toBe('—')
  })
})