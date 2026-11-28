// Shared React hooks.

import { useCallback, useEffect, useRef, useState } from 'react'

export interface AsyncState<T> {
  data: T | null
  error: unknown | null
  loading: boolean
  reload: () => void
}

/** Runs an async loader on mount and exposes data/error/loading + reload. */
export function useAsync<T>(loader: () => Promise<T>, deps: unknown[] = []): AsyncState<T> {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(true)
  const [tick, setTick] = useState(0)
  const loaderRef = useRef(loader)
  loaderRef.current = loader

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    loaderRef.current()
      .then((value) => {
        if (!cancelled) setData(value)
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tick, ...deps])

  const reload = useCallback(() => setTick((t) => t + 1), [])

  return { data, error, loading, reload }
}

/** SetInterval abstraction with auto-cleanup (used for degraded reconnect polling). */
export function useInterval(fn: () => void, delayMs: number | null): void {
  const fnRef = useRef(fn)
  fnRef.current = fn
  useEffect(() => {
    if (delayMs == null) return
    const id = window.setInterval(() => fnRef.current(), delayMs)
    return () => window.clearInterval(id)
  }, [delayMs])
}