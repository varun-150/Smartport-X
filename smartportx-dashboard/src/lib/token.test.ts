import { beforeEach, describe, expect, it } from 'vitest'
import { tokenStore } from './token'

const USER = { username: 'ops', role: 'TERMINAL_OP' }

describe('tokenStore', () => {
  beforeEach(() => {
    tokenStore.clear()
  })

  it('starts with no session', () => {
    expect(tokenStore.hasSession()).toBe(false)
    expect(tokenStore.getAccessToken()).toBeNull()
    expect(tokenStore.getRefreshToken()).toBeNull()
  })

  it('round-trips access + refresh tokens', () => {
    tokenStore.save('access-1', 'refresh-1')
    expect(tokenStore.hasSession()).toBe(true)
    expect(tokenStore.getAccessToken()).toBe('access-1')
    expect(tokenStore.getRefreshToken()).toBe('refresh-1')
  })

  it('clears the refresh token when login has none', () => {
    tokenStore.save('access-2', 'refresh-2')
    tokenStore.save('access-3', null)
    expect(tokenStore.getAccessToken()).toBe('access-3')
    expect(tokenStore.getRefreshToken()).toBeNull()
  })

  it('round-trips the cached user', () => {
    expect(tokenStore.readUser()).toBeNull()
    tokenStore.saveUser(USER)
    expect(tokenStore.readUser()).toEqual(USER)
    tokenStore.saveUser(null)
    expect(tokenStore.readUser()).toBeNull()
  })

  it('clear() wipes the whole session', () => {
    tokenStore.save('a', 'r')
    tokenStore.saveUser(USER)
    tokenStore.clear()
    expect(tokenStore.hasSession()).toBe(false)
    expect(tokenStore.readUser()).toBeNull()
  })
})