// Central config from Vite env vars with dev-first defaults.
// REST goes through the Vite proxy /api -> gateway:8080; WS through /ws -> gateway:8080.

const str = (value: string | undefined, fallback: string): string =>
  value === undefined || value === '' ? fallback : value

export const env = {
  /** REST base. Default '/api' rides the Vite dev proxy (same-origin, no CORS). */
  apiBaseUrl: str(import.meta.env.VITE_API_BASE_URL as string | undefined, '/api'),
  /** STOMP endpoint. Default '/ws' rides the Vite dev proxy. */
  wsEndpoint: str(import.meta.env.VITE_WS_ENDPOINT as string | undefined, '/ws'),
  /** HTTP timeout for API calls (ms). */
  requestTimeoutMs: Number(str(import.meta.env.VITE_API_TIMEOUT_MS as string | undefined, '15000')),
  /** Reconnect delay for the STOMP client (ms). */
  wsReconnectDelayMs: Number(str(import.meta.env.VITE_WS_RECONNECT_MS as string | undefined, '4000')),
} as const