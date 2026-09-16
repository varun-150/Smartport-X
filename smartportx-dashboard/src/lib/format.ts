export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '\u2013';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '\u2013';
  return d.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatTime(iso: string | null | undefined): string {
  if (!iso) return '\u2013';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '\u2013';
  return d.toLocaleString(undefined, {
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatTimeOnly(iso: string | null | undefined): string {
  return formatTime(iso);
}

export function formatClock(date = new Date()): string {
  return date.toLocaleTimeString(undefined, {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

export function formatDuration(minutes: number | null | undefined): string {
  if (minutes == null || Number.isNaN(minutes)) return '\u2013';
  const m = Math.round(minutes);
  const h = Math.floor(m / 60);
  const rest = m % 60;
  if (h === 0) return `${rest}m`;
  if (rest === 0) return `${h}h`;
  return `${h}h ${rest}m`;
}

export function formatNumber(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '\u2013';
  return new Intl.NumberFormat('en-US').format(value);
}

export function formatTeu(value: number | null | undefined): string {
  return formatNumber(value);
}

export function formatWeight(kg: number | null | undefined): string {
  if (kg == null || Number.isNaN(kg)) return '\u2013';
  if (Math.abs(kg) >= 1000) return `${(kg / 1000).toFixed(1)} t`;
  return `${Math.round(kg)} kg`;
}

export function formatPercent(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '\u2013';
  return `${value.toFixed(1)}%`;
}

export function truncate(value: string, max = 34): string {
  if (value.length <= max) return value;
  return `${value.slice(0, max - 1)}\u2026`;
}

export function titleCase(value: string | null | undefined): string {
  if (!value) return '\u2013';
  return value
    .toLowerCase()
    .split(/[_\s]+/)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}