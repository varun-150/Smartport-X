// STOMP realtime layer.
//
// Connects STOMP over SockJS through the Vite /ws proxy to the gateway. Exposes a
// provider + hook that broadcast payloads per topic to subscribed React components and
// surfaces an honest connection state. No fabricated messages: when connectivity is
// impossible the state machine reports DEGRADED and callers render clearly-labelled gaps.

import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { env } from '../config/env'
import { useAuth } from './auth'

export type RealtimeStatus = 'CONNECTING' | 'CONNECTED' | 'RECONNECTING' | 'DEGRADED' | 'DISABLED'

interface RealtimeContextValue {
  status: RealtimeStatus
  /** Subscribe to a STOMP destination; returns an unsubscribe function. */
  subscribe: (destination: string, onMessage: (payload: unknown) => void) => () => void
}

const RealtimeContext = createContext<RealtimeContextValue | null>(null)

/** Role-gated topic map (mirrors gateway route definitions). */
const TOPIC_ROLES: Record<string, string[]> = {
  '/topic/dashboard/summary': ['*'],
  '/topic/yard/status': ['TERMINAL_OP', 'YARD_MANAGER', 'ADMIN'],
  '/topic/yard/zone': ['TERMINAL_OP', 'YARD_MANAGER', 'ADMIN'],
  '/topic/gate/status': ['TERMINAL_OP', 'GATE_OPERATOR', 'ADMIN'],
  '/topic/vessel/arrivals': ['TERMINAL_OP', 'ADMIN'],
  '/topic/vessel/departures': ['TERMINAL_OP', 'ADMIN'],
  '/topic/containers/movements': ['*'],
  '/topic/containers/status': ['*'],
  '/topic/containers/alerts': ['*'],
  '/topic/appointments': ['GATE_OPERATOR', 'ADMIN'],
  '/topic/customs/clearances': ['TERMINAL_OP', 'ADMIN'],
  '/topic/notifications': ['*'],
}

function topicAllowed(destination: string, role: string): boolean {
  const base = Object.keys(TOPIC_ROLES).find((k) => {
    if (k.endsWith('*')) return destination.startsWith(k.slice(0, -1))
    return destination.startsWith(k)
  })
  if (!base) return false
  const roles = TOPIC_ROLES[base] ?? []
  return roles.includes('*') || roles.includes(role)
}

export function RealtimeProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const [status, setStatus] = useState<RealtimeStatus>('DISABLED')
  const clientRef = useRef<Client | null>(null)
  const subsRef = useRef<Map<string, Set<(payload: unknown) => void>>>(new Map())

  const broadcast = useCallback((destination: string, payload: unknown) => {
    subsRef.current.get(destination)?.forEach((handler) => {
      try {
        handler(payload)
      } catch {
        // component handler must not kill the socket
      }
    })
  }, [])

  useEffect(() => {
    if (!user) {
      setStatus('DISABLED')
      return
    }

    // SockJS is a UMD build; the default import is a constructor function.
    const SockJSCls = SockJS as unknown as { new (url: string): unknown }

    const client = new Client({
      brokerURL: undefined,
      webSocketFactory: () => new SockJSCls(env.wsEndpoint) as unknown as WebSocket,
      reconnectDelay: env.wsReconnectDelayMs,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      onConnect: () => {
        setStatus('CONNECTED')
        // Subscribe to every role-permitted topic once the socket is up.
        Object.keys(TOPIC_ROLES).forEach((dest) => {
          if (!topicAllowed(dest, user.role)) return
          client.subscribe(dest, (message: IMessage) => {
            try {
              broadcast(dest, JSON.parse(message.body))
            } catch {
              broadcast(dest, { raw: message.body })
            }
          })
        })
      },
      onStompError: () => setStatus('RECONNECTING'),
      onWebSocketClose: () => setStatus('RECONNECTING'),
      onWebSocketError: () => setStatus('DEGRADED'),
    })

    setStatus('CONNECTING')
    clientRef.current = client
    client.activate()

    return () => {
      client.deactivate()
      clientRef.current = null
    }
  }, [user, broadcast])

  const subscribe = useCallback((destination: string, onMessage: (payload: unknown) => void) => {
    let set = subsRef.current.get(destination)
    if (!set) {
      set = new Set()
      subsRef.current.set(destination, set)
    }
    set.add(onMessage)
    return () => {
      set.delete(onMessage)
      if (set.size === 0) subsRef.current.delete(destination)
    }
  }, [])

  const value = useMemo(() => ({ status, subscribe }), [status, subscribe])

  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>
}

export function useRealtime(): RealtimeContextValue {
  const ctx = useContext(RealtimeContext)
  if (!ctx) throw new Error('useRealtime must be used within <RealtimeProvider>')
  return ctx
}