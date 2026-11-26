// STOMP realtime layer.
//
// Subscriptions are created for the exact destinations requested by mounted UI
// features. Authorization rules mirror Notification's TopicAuthorizationService;
// dynamic one-segment topics (container IDs, yard zones, gate lanes, etc.) are
// supported instead of subscribing to mismatched static aliases.

import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { env } from '../config/env'
import { useAuth } from './auth'

export type RealtimeStatus = 'CONNECTING' | 'CONNECTED' | 'RECONNECTING' | 'DEGRADED' | 'DISABLED'

interface RealtimeContextValue {
  status: RealtimeStatus
  /** Subscribe to an authorized STOMP destination; returns an unsubscribe function. */
  subscribe: (destination: string, onMessage: (payload: unknown) => void) => () => void
}

const RealtimeContext = createContext<RealtimeContextValue | null>(null)

/** Mirrors Notification's server-side TopicAuthorizationService rules. */
const TOPIC_RULES: Array<{ pattern: string; roles: string[] }> = [
  { pattern: '/topic/yard-status', roles: ['TERMINAL_OP'] },
  { pattern: '/topic/yard-zone/*', roles: ['YARD_MANAGER'] },
  { pattern: '/topic/gate-status', roles: ['GATE_OPERATOR'] },
  { pattern: '/topic/gate-lane/*', roles: ['GATE_OPERATOR'] },
  { pattern: '/topic/vessel-status', roles: ['TERMINAL_OP'] },
  { pattern: '/topic/vessel/*', roles: ['TERMINAL_OP'] },
  { pattern: '/topic/containers/*', roles: ['*'] },
  { pattern: '/topic/appointments/*', roles: ['GATE_OPERATOR'] },
  { pattern: '/topic/customs/*', roles: ['TERMINAL_OP'] },
  { pattern: '/topic/dashboard/summary', roles: ['*'] },
  { pattern: '/topic/alerts', roles: ['*'] },
  { pattern: '/topic/alerts/*', roles: ['*'] },
]

export function topicAllowed(destination: string, role: string): boolean {
  if (!destination || !role) return false
  if (role === 'ADMIN') return true

  const actual = destination.split('/').filter(Boolean)
  const rule = TOPIC_RULES.find(({ pattern }) => {
    const expected = pattern.split('/').filter(Boolean)
    return expected.length === actual.length
      && expected.every((segment, index) => segment === '*' || segment === actual[index])
  })
  return Boolean(rule && (rule.roles.includes('*') || rule.roles.includes(role)))
}

export function RealtimeProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const [status, setStatus] = useState<RealtimeStatus>('DISABLED')
  const clientRef = useRef<Client | null>(null)
  const handlersRef = useRef<Map<string, Set<(payload: unknown) => void>>>(new Map())
  const socketSubscriptionsRef = useRef<Map<string, StompSubscription>>(new Map())

  const broadcast = useCallback((destination: string, payload: unknown) => {
    handlersRef.current.get(destination)?.forEach((handler) => {
      try {
        handler(payload)
      } catch {
        // A component handler must not kill the shared socket.
      }
    })
  }, [])

  const ensureSocketSubscription = useCallback((destination: string) => {
    if (!user || !topicAllowed(destination, user.role)) return
    const client = clientRef.current
    if (!client?.connected || socketSubscriptionsRef.current.has(destination)) return

    const subscription = client.subscribe(destination, (message: IMessage) => {
      try {
        broadcast(destination, JSON.parse(message.body))
      } catch {
        broadcast(destination, { raw: message.body })
      }
    })
    socketSubscriptionsRef.current.set(destination, subscription)
  }, [user, broadcast])

  useEffect(() => {
    if (!user) {
      setStatus('DISABLED')
      return
    }

    const SockJSCls = SockJS as unknown as { new (url: string): unknown }
    const client = new Client({
      brokerURL: undefined,
      webSocketFactory: () => new SockJSCls(env.wsEndpoint) as unknown as WebSocket,
      reconnectDelay: env.wsReconnectDelayMs,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      onConnect: () => {
        setStatus('CONNECTED')
        handlersRef.current.forEach((_handlers, destination) => ensureSocketSubscription(destination))
      },
      onStompError: () => setStatus('RECONNECTING'),
      onWebSocketClose: () => {
        socketSubscriptionsRef.current.clear()
        setStatus('RECONNECTING')
      },
      onWebSocketError: () => setStatus('DEGRADED'),
    })

    setStatus('CONNECTING')
    clientRef.current = client
    client.activate()

    return () => {
      socketSubscriptionsRef.current.forEach((subscription) => subscription.unsubscribe())
      socketSubscriptionsRef.current.clear()
      clientRef.current = null
      void client.deactivate()
    }
  }, [user, ensureSocketSubscription])

  const subscribe = useCallback((destination: string, onMessage: (payload: unknown) => void) => {
    if (!user || !topicAllowed(destination, user.role)) return () => undefined

    let handlers = handlersRef.current.get(destination)
    if (!handlers) {
      handlers = new Set()
      handlersRef.current.set(destination, handlers)
    }
    handlers.add(onMessage)
    ensureSocketSubscription(destination)

    return () => {
      const current = handlersRef.current.get(destination)
      current?.delete(onMessage)
      if (current?.size === 0) {
        handlersRef.current.delete(destination)
        socketSubscriptionsRef.current.get(destination)?.unsubscribe()
        socketSubscriptionsRef.current.delete(destination)
      }
    }
  }, [user, ensureSocketSubscription])

  const value = useMemo(() => ({ status, subscribe }), [status, subscribe])

  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>
}

export function useRealtime(): RealtimeContextValue {
  const ctx = useContext(RealtimeContext)
  if (!ctx) throw new Error('useRealtime must be used within <RealtimeProvider>')
  return ctx
}
