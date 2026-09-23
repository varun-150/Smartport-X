// Yard contracts (yard-service): layout, blocks, equipment, optimization.

export type BlockType = 'EXPORT' | 'IMPORT' | 'TRANSIT' | 'REFRIGERATED' | 'DANGEROUS' | 'EMPTY'

export type EquipmentType = 'RTG' | 'ASC'

export type EquipmentStatus = 'AVAILABLE' | 'ASSIGNED' | 'OUT_OF_SERVICE'

export type OptimizationStrategy = 'FIFO' | 'WEIGHT_CLUSTER' | 'DEPARTURE_CLUSTER' | 'BALANCED'

export type OptimizationStatus = 'COMPLETED' | 'FAILED' | 'IN_PROGRESS'

export type TierStatus = 'OCCUPIED' | 'EMPTY'

export interface YardMetrics {
  yardId: string
  totalSlots: number
  occupiedSlots: number
  utilizationRate: number
  movesPerHour: number
  slotsAvailable: number
  blocksTotal: number
  reeferLoad: number
  dgLoad: number
  period: string
  measuredAt: string
}

export interface BlockSummary {
  id: string
  blockCode: string
  type: BlockType | null
  totalSlots: number
  occupiedSlots: number
  utilizationRate: number
}

export interface Tier {
  tierNumber: number
  position: string
  status: TierStatus
  containerId: string | null
  containerNumber: string | null
  containerType: ContainerType | null
  dangerousGoods: boolean
  estimatedDeparture: string | null
}

// imported from container types for the slot notation
import type { ContainerType } from './container'
export interface Row {
  rowNumber: number
  tiers: Tier[]
}

export interface Bay {
  bayNumber: number
  rows: Row[]
}

export interface Block {
  id: string
  blockCode: string
  type: BlockType | null
  totalSlots: number
  occupiedSlots: number
  utilizationRate: number
  bays: Bay[]
}

export interface LayoutResponse {
  yardId: string
  yardName: string
  totalBlocks: number
  totalSlots: number
  occupiedSlots: number
  utilizationRate: number
  lastUpdated: string
  blocks: Block[]
}

export interface YardEquipment {
  id: string
  equipmentCode: string
  type: EquipmentType
  status: EquipmentStatus
  currentBlockId: string | null
  currentBlockCode: string | null
  operatorId: string | null
  utilizationRate: number
  lastAssignedAt: string | null
}

export interface YardMetricsBlock {
  blockCode: string
  type: BlockType | null
  capacity: number
  occupied: number
  utilizationRate: number
  movesPerHour: number
}