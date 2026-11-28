import { describe, expect, it } from 'vitest'
import { formatDateTime, formatDuration, formatNumber, formatPercent, formatTeu, formatTime, formatWeight } from './format'

describe('formatDateTime', () => {
  it('renders local "02 Feb · 14:32" from an ISO string', () => {
    const d = new Date(2026, 1, 2, 14, 32)
    expect(formatDateTime(d.toISOString())).toBe('02 Feb · 14:32')
  })

  it('uses single-digit pad for early times/days', () => {
    const d = new Date(2026, 0, 5, 9, 5)
    expect(formatDateTime(d.toISOString())).toBe('05 Jan · 09:05')
  })

  it('returns the em dash for empty or invalid input', () => {
    expect(formatDateTime(null)).toBe('—')
    expect(formatDateTime(undefined)).toBe('—')
    expect(formatDateTime('not-a-date')).toBe('—')
  })
})

describe('formatTime', () => {
  it('renders "14:32"', () => {
    const d = new Date(2026, 1, 2, 14, 32)
    expect(formatTime(d.toISOString())).toBe('14:32')
  })

  it('returns the em dash for empty/invalid input', () => {
    expect(formatTime(null)).toBe('—')
    expect(formatTime('bogus')).toBe('—')
  })
})

describe('formatDuration', () => {
  it('renders minutes-only durations', () => {
    expect(formatDuration(120_000)).toBe('2m')
  })

  it('renders padded hours and minutes', () => {
    expect(formatDuration(4_320_000)).toBe('1h 12m')
  })

  it('renders the em dash for null/negative', () => {
    expect(formatDuration(null)).toBe('—')
    expect(formatDuration(-1)).toBe('—')
  })
})

describe('formatNumber', () => {
  it('adds thousands separators', () => {
    expect(formatNumber(1234567)).toBe('1,234,567')
  })

  it('returns the em dash for null/NaN', () => {
    expect(formatNumber(null)).toBe('—')
    expect(formatNumber(Number.NaN)).toBe('—')
  })
})

describe('formatTeu', () => {
  it('trims the trailing .0', () => {
    expect(formatTeu(12)).toBe('12')
    expect(formatTeu(12.04)).toBe('12')
  })

  it('keeps one decimal otherwise', () => {
    expect(formatTeu(12.34)).toBe('12.3')
  })

  it('returns the em dash for null', () => {
    expect(formatTeu(null)).toBe('—')
  })
})

describe('formatWeight', () => {
  it('formats kg with separators', () => {
    expect(formatWeight(12400)).toBe('12,400 kg')
  })

  it('returns the em dash for null', () => {
    expect(formatWeight(null)).toBe('—')
  })
})

describe('formatPercent', () => {
  it('keeps one decimal', () => {
    expect(formatPercent(23.5)).toBe('23.5%')
  })

  it('returns the em dash for null', () => {
    expect(formatPercent(null)).toBe('—')
  })
})