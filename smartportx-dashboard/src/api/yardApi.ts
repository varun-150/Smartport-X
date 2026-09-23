// Yard API client — paths verified against yard-service controllers.

import { get, put } from '../lib/http'
import type {
  Block,
  LayoutResponse,
  ListResponse,
  YardEquipment,
  YardMetrics,
  YardMetricsBlock,
} from '../types'

export const yardApi = {
  layout: (params: { includeEmpty?: boolean; blockFilter?: string } = {}) =>
    get<LayoutResponse>('/v1/yard/layout', {
      ...(params.includeEmpty != null ? { includeEmpty: params.includeEmpty } : {}),
      ...(params.blockFilter ? { blockFilter: params.blockFilter } : {}),
    }),

  blocks: () => get<ListResponse<YardMetricsBlock>>('/v1/yard/blocks'),

  block: (id: string) => get<Block>(`/v1/yard/blocks/${id}`),

  metrics: (period: '24h' | '7d' | '30d' = '24h') =>
    get<YardMetrics>('/v1/yard/metrics', { period }),

  metricsByBlock: () => get<YardMetricsBlock[]>('/v1/yard/metrics/blocks'),

  equipment: () => get<ListResponse<YardEquipment>>('/v1/yard/equipment'),

  equipmentItem: (id: string) => get<YardEquipment>(`/v1/yard/equipment/${id}`),

  updateEquipment: (id: string, req: Partial<YardEquipment>) =>
    put<YardEquipment>(`/v1/yard/equipment/${id}`, req),
}