// Yard API client — paths verified against yard-service controllers.
// The yard service returns `{ data: [...] }` envelopes whose field names differ from the
// dashboard view models. Normalisation is performed here so feature components consume
// the documented frontend types without crashing on undefined fields.

import { get, put } from '../lib/http'
import type {
  Block,
  BlockType,
  LayoutResponse,
  ListResponse,
  YardEquipment,
  YardMetrics,
  YardMetricsBlock,
} from '../types'

type DataEnvelope<T> = { data: T[] }

type RawBlockMetrics = {
  blockId: string
  blockCode: string
  blockType: string | null
  totalSlots: number
  occupiedSlots: number
  utilizationRate: number
  reshufflingRate?: number
}

type RawEquipment = {
  equipmentId: string
  code: string
  equipmentType: YardEquipment['type']
  assignedBlockId: string | null
  status: YardEquipment['status']
  movesTotal: number
  updatedAt?: string
}

type RawYardMetrics = {
  yardId: string
  period: string
  generatedAt: string
  totalSlots: number
  occupiedSlots: number
  availableSlots: number
  utilizationRate: number
  refrigeratedCount: number
  dangerousGoodsCount: number
  averageMovesPerHour: number | null
  totalMoves24h?: number | null
}

export const yardApi = {
  layout: (params: { includeEmpty?: boolean; blockFilter?: string } = {}) =>
    get<LayoutResponse>('/v1/yard/layout', {
      ...(params.includeEmpty != null ? { includeEmpty: params.includeEmpty } : {}),
      ...(params.blockFilter ? { blockFilter: params.blockFilter } : {}),
    }),

  blocks: async () => {
    const response = await get<DataEnvelope<{ blockId: string; blockCode: string; type: BlockType | null; totalSlots: number; occupiedSlots: number; utilizationRate: number }>>('/v1/yard/blocks')
    const items = response?.data ?? []
    return {
      items,
      nextCursor: null,
      totalCount: items.length,
    } satisfies ListResponse<{ blockId: string; blockCode: string; type: BlockType | null; totalSlots: number; occupiedSlots: number; utilizationRate: number }>
  },

  block: (id: string) => get<Block>(`/v1/yard/blocks/${id}`),

  metrics: async (period: '24h' | '7d' | '30d' = '24h'): Promise<YardMetrics> => {
    const raw = await get<RawYardMetrics>('/v1/yard/metrics', { period })
    return {
      yardId: raw.yardId,
      period: raw.period,
      measuredAt: raw.generatedAt,
      totalSlots: raw.totalSlots,
      occupiedSlots: raw.occupiedSlots,
      slotsAvailable: raw.availableSlots,
      utilizationRate: raw.utilizationRate,
      blocksTotal: 0,
      movesPerHour: raw.averageMovesPerHour ?? raw.totalMoves24h ?? 0,
      reeferLoad: raw.refrigeratedCount,
      dgLoad: raw.dangerousGoodsCount,
    }
  },

  metricsByBlock: async (): Promise<YardMetricsBlock[]> => {
    const response = await get<DataEnvelope<RawBlockMetrics>>('/v1/yard/metrics/blocks')
    return (response?.data ?? []).map((block) => ({
      blockCode: block.blockCode,
      type: block.blockType as BlockType | null,
      capacity: block.totalSlots,
      occupied: block.occupiedSlots,
      utilizationRate: block.utilizationRate,
      movesPerHour: Math.round((block.reshufflingRate ?? 0) * 100),
    }))
  },

  equipment: async (): Promise<ListResponse<YardEquipment>> => {
    const response = await get<DataEnvelope<RawEquipment>>('/v1/yard/equipment')
    const items: YardEquipment[] = (response?.data ?? []).map((unit) => ({
      id: unit.equipmentId,
      equipmentCode: unit.code,
      type: unit.equipmentType,
      status: unit.status,
      currentBlockId: unit.assignedBlockId,
      currentBlockCode: null,
      operatorId: null,
      utilizationRate: 0,
      lastAssignedAt: unit.updatedAt ?? null,
    }))
    return { items, nextCursor: null, totalCount: items.length }
  },

  equipmentItem: (id: string) => get<YardEquipment>(`/v1/yard/equipment/${id}`),

  updateEquipment: (id: string, req: Partial<YardEquipment>) =>
    put<YardEquipment>(`/v1/yard/equipment/${id}`, req),
}
