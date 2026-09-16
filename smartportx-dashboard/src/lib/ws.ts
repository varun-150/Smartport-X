import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { env } from '../config/env';
import { accessToken } from './token';

export type TopicRule =
  | { roles: null }
  | { roles: readonly string[] };

const topicRules: Record<string, TopicRule> = {
  [`/topic/dashboard/summary`]: { roles: null },

  '/topic/yard/status': { roles: ['TERMINAL_OP'] },
  '/topic/yard/zone/*': { roles: ['YARD_MANAGER'] },

  '/topic/gate/status': { roles: ['GATE_OPERATOR'] },
  '/topic/gate/lane/*': { roles: ['GATE_OPERATOR'] },

  '/topic/vessel/arrivals': { roles: ['TERMINAL_OP'] },
  '/topic/vessel/departures': { roles: ['TERMINAL_OP'] },
  '/topic/vessel/discharge-plans': { roles: ['TERMINAL_OP'] },

  '/topic/containers/movements': { roles: null },
  '/topic/containers/status': { roles: null },
  '/topic/containers/alerts': { roles: null },

  '/topic/appointments/*': { roles: ['GATE_OPERATOR'] },

  '/topic/customs/clearances': { roles: ['TERMINAL_OP'] },
};

function wildcardToRegex(pattern: string): RegExp {
  const escaped = pattern.replace(/[.+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`^${escaped.replace(/\*/g, '.*')}$`);
}

export function topicAllowed(topic: string, roles: readonly string[]): boolean {
  if (roles.includes('ADMIN')) return true;
  const rule = Object.entries(topicRules).find(([pattern]) =>
    wildcardToRegex(pattern).test(topic),
  )?.[1];
  if (!rule) return false;
  if (rule.roles === null) return true;
  return rule.roles.some((r) => roles.includes(r));
}

let client: Client | null = null;

interface Subscription {
  unsubscribe: () => void;
}

export function connectSocket(): void {
  if (client?.active) return;

  const token = accessToken();
  client = new Client({
    webSocketFactory: () => new SockJS(`${env.apiBaseUrl}/ws`),
    reconnectDelay: 5000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
    beforeConnect: () => {
      const t = accessToken();
      if (t && client) {
        client.connectHeaders = { Authorization: `Bearer ${t}` };
      }
    },
    debug: () => undefined,
  });
  client.activate();
}

export function disconnectSocket(): void {
  if (client?.active) {
    void client.deactivate();
  }
  client = null;
}

export function isSocketActive(): boolean {
  return client?.active ?? false;
}

export function subscribeTopic(
  topic: string,
  roles: readonly string[],
  onMessage: (body: unknown) => void,
): Subscription | null {
  if (!client?.active) return null;
  if (!topicAllowed(topic, roles)) return null;

  const sub = client.subscribe(topic, (frame: IMessage) => {
    try {
      onMessage(JSON.parse(frame.body) as unknown);
    } catch {
      onMessage(frame.body);
    }
  });

  return {
    unsubscribe: () => sub.unsubscribe(),
  };
}