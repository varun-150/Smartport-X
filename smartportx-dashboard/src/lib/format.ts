// Formatting helpers — stable, locale-neutral, tabular-friendly.

const pad = (n: number): string => String(n).padStart(2, '0')

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/** ISO string -> "02 Feb · 14:32" (or "—" when input is empty/invalid). */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return `${pad(d.getDate())} ${MONTHS[d.getMonth()]} · ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** ISO string -> "14:32" (or "—"). */
export function formatTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** Duration in ms -> "3h 12m". */
export function formatDuration(ms: number | null | undefined): string {
  if (ms == null || ms < 0) return '—'
  const totalMinutes = Math.floor(ms / 60000)
  const h = Math.floor(totalMinutes / 60)
  const m = totalMinutes % 60
  if (h === 0) return `${m}m`
  return `${h}h ${pad(m)}m`
}

/** Number with thousands separators. */
export function formatNumber(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—'
  return value.toLocaleString('en-US')
}

/** TEU figure: keep 1 decimal, trim ".0". */
export function formatTeu(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—'
  const v = value.toFixed(1)
  return v.replace(/\.0$/, '')
}

/** Weight kg -> "12,400 kg" (or tonnes flagged). */
export function formatWeight(kg: number | null | undefined): string {
  if (kg == null || Number.isNaN(kg)) return '—'
  return `${formatNumber(kg)} kg`
}

export function formatPercent(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—'
  return `${value.toFixed(1)}%`
}