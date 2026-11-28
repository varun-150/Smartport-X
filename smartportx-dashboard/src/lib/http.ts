// Shared HTTP client.
//
// - baseURL from env (default '/api' -> Vite proxy -> gateway:8080)
// - injects the access token as a Bearer header
// - on 401: tries a single-flight silent refresh via /v1/auth/refresh, then replays the
//   original request once. If the refresh itself fails the session is cleared and listeners
//   are notified so the app can route to /login.
// - parses RFC 9457 problem documents into ApiError.

import axios, { AxiosError, AxiosHeaders, type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { env } from '../config/env'
import { ApiError, type Problem } from '../types/api'
import { tokenStore } from './token'

export const UNAUTHORIZED_EVENT = 'smartportx:unauthorized'

const instance: AxiosInstance = axios.create({
  baseURL: env.apiBaseUrl,
  timeout: env.requestTimeoutMs,
  headers: { 'Content-Type': 'application/json' },
})

function toProblem(status: number, data: unknown): Problem {
  if (data && typeof data === 'object' && 'title' in data) {
    const p = data as Record<string, unknown>
    return {
      type: typeof p.type === 'string' ? p.type : 'about:blank',
      title: typeof p.title === 'string' ? p.title : 'Request failed',
      status: typeof p.status === 'number' ? p.status : status,
      detail: typeof p.detail === 'string' ? p.detail : undefined,
      fieldErrors: p.fieldErrors as Record<string, string> | undefined,
    }
  }
  return { type: 'about:blank', title: 'Request failed', status }
}

export function getErrorMessage(err: unknown): string {
  if (err instanceof ApiError) return err.detail ?? err.title
  if (err instanceof Error) return err.message
  return 'Unexpected error'
}

// Single-flight refresh: concurrent 401s share one refresh promise.
let refreshPromise: Promise<boolean> | null = null
let unauthorizedHandlers: Array<() => void> = []

export function onUnauthorized(handler: () => void): () => void {
  unauthorizedHandlers.push(handler)
  return () => {
    unauthorizedHandlers = unauthorizedHandlers.filter((h) => h !== handler)
  }
}

function fireUnauthorized() {
  tokenStore.clear()
  unauthorizedHandlers.forEach((h) => {
    try {
      h()
    } catch {
      // handler threw — ignore, listeners must not break the queue
    }
  })
  window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
}

let refreshingAttemptInProgress = false

async function performRefresh(): Promise<boolean> {
  const refreshToken = tokenStore.getRefreshToken()
  if (!refreshToken) return false
  refreshingAttemptInProgress = true
  try {
    const resp = await axios.post(
      `${env.apiBaseUrl}/v1/auth/refresh`,
      { refreshToken },
      { timeout: env.requestTimeoutMs },
    )
    const data = resp.data as { accessToken: string; expiresIn: number }
    if (!data.accessToken) return false
    tokenStore.save(data.accessToken, refreshToken)
    return true
  } catch {
    return false
  } finally {
    refreshingAttemptInProgress = false
  }
}

function refreshOnce(): Promise<boolean> {
  if (refreshPromise) return refreshPromise
  refreshPromise = performRefresh().finally(() => {
    refreshPromise = null
  })
  return refreshPromise
}

instance.interceptors.request.use((config) => {
  const token = tokenStore.getAccessToken()
  if (token) {
    const headers = AxiosHeaders.from(config.headers)
    headers.set('Authorization', `Bearer ${token}`)
    config.headers = headers
  }
  return config
})

instance.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined
    const status = error.response?.status ?? 0

    // Network / timeout / 5xx from the edge → connection-style error.
    if (!error.response) {
      return Promise.reject(new ApiError(0, 'Network error', error.message, undefined, true))
    }

    // Auth endpoint failures must not recurse into the refresh path.
    const isAuthCall = config?.url?.startsWith('/v1/auth')
    const isRefreshCall = config?.url?.includes('/refresh')

    if (status === 401 && config && !config._retried && !isAuthCall && !isRefreshCall) {
      const ok = await refreshOnce()
      if (ok) {
        config._retried = true
        const retryInstance = axios.create({ baseURL: env.apiBaseUrl })
        const tok = tokenStore.getAccessToken()
        if (tok) retryInstance.defaults.headers.common.Authorization = `Bearer ${tok}`
        try {
          return await retryInstance.request(config)
        } catch (retryError) {
          return Promise.reject(mapError(retryError as AxiosError))
        }
      }
      fireUnauthorized()
      return Promise.reject(new ApiError(401, 'Unauthorized', 'Session expired. Please sign in again.'))
    }

    return Promise.reject(mapError(error))
  },
)

function mapError(error: AxiosError): ApiError {
  const status = error.response?.status ?? 0
  const problem = toProblem(status, error.response?.data)
  const isNetwork = !error.response
  return new ApiError(
    status,
    problem.title,
    problem.detail,
    problem.fieldErrors,
    isNetwork || status >= 500,
  )
}

/** Low-level typed request used by the api/ modules. */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await instance.request<T>(config)
  return response.data
}

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return request<T>({ method: 'GET', url, params })
}

export async function post<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ method: 'POST', url, data })
}

export async function put<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ method: 'PUT', url, data })
}

export async function patch<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ method: 'PATCH', url, data })
}

export async function del<T = void>(url: string, data?: unknown): Promise<T> {
  return request<T>({ method: 'DELETE', url, data })
}

export { refreshingAttemptInProgress }