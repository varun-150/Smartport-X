import { describe, expect, it } from 'vitest'
import { topicAllowed } from './realtime'

describe('topicAllowed', () => {
  it('allows dynamic container topics for every authenticated role', () => {
    expect(topicAllowed('/topic/containers/MSKU1234567', 'READONLY')).toBe(true)
    expect(topicAllowed('/topic/containers/MSKU1234567', 'GATE_OPERATOR')).toBe(true)
    expect(topicAllowed('/topic/containers/MSKU1234567/extra', 'READONLY')).toBe(false)
  })

  it('matches yard status and yard-zone permissions separately', () => {
    expect(topicAllowed('/topic/yard-status', 'TERMINAL_OP')).toBe(true)
    expect(topicAllowed('/topic/yard-zone/B1', 'YARD_MANAGER')).toBe(true)
    expect(topicAllowed('/topic/yard-zone/B1', 'TERMINAL_OP')).toBe(false)
    expect(topicAllowed('/topic/yard-zone/B1/extra', 'YARD_MANAGER')).toBe(false)
  })

  it('restricts gate topics and appointments to gate operators', () => {
    expect(topicAllowed('/topic/gate-status', 'GATE_OPERATOR')).toBe(true)
    expect(topicAllowed('/topic/gate-lane/L1', 'GATE_OPERATOR')).toBe(true)
    expect(topicAllowed('/topic/appointments/APT-1', 'GATE_OPERATOR')).toBe(true)
    expect(topicAllowed('/topic/gate-status', 'TERMINAL_OP')).toBe(false)
  })

  it('allows terminal operators to receive vessel and customs topics', () => {
    expect(topicAllowed('/topic/vessel-status', 'TERMINAL_OP')).toBe(true)
    expect(topicAllowed('/topic/vessel/VSL-1', 'TERMINAL_OP')).toBe(true)
    expect(topicAllowed('/topic/customs/MSKU1234567', 'TERMINAL_OP')).toBe(true)
    expect(topicAllowed('/topic/customs/MSKU1234567', 'READONLY')).toBe(false)
  })

  it('allows authenticated dashboard and alert topics', () => {
    expect(topicAllowed('/topic/dashboard/summary', 'READONLY')).toBe(true)
    expect(topicAllowed('/topic/alerts', 'READONLY')).toBe(true)
    expect(topicAllowed('/topic/alerts/warning', 'READONLY')).toBe(true)
  })

  it('allows administrators and denies unknown destinations for other roles', () => {
    expect(topicAllowed('/topic/anything', 'ADMIN')).toBe(true)
    expect(topicAllowed('/topic/anything', 'READONLY')).toBe(false)
    expect(topicAllowed('/topic/yard-status', '')).toBe(false)
  })
})
