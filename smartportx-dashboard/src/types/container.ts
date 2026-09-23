// Container contracts (container-service).

import type { PageResponse } from './api'

export type ContainerStatus =
  | 'REGISTERED'
  | 'AT_GATE'
  | 'IN_YARD'
  | 'LOADED'
  | 'DISCHARGED'
  | 'AT_GATE_OUT'
  | 'DEPARTED'
  | 'HELD'

export type ContainerType = '20DC' | '40DC' | '40HC' | '45HC' | '20RF' | '40RF' | '20TK' | '40TK'

export type MovementType =
  | 'GATE_IN'
  | 'GATE_OUT'
  | 'YARD_MOVE'
  | 'VESSEL_LOAD'
  | 'VESSEL_DISCHARGE'

export interface ContainerResponse {
  id: string
  containerNumber: string
  isoCode: string
  type: ContainerType
  sizeFeet: number
  owner: string
  weightKg: number
  status: ContainerStatus
  location: string | null
  dangerousGoods: boolean
  restricted: boolean
  temperatureCelsius: number | null
  createdAt: string
  updatedAt: string
  movementCount: number
  lastMovementAt: string | null
}

export interface ContainerMovement {
  id: string
  containerId: string
  containerNumber: string
  type: MovementType
  fromLocation: string | null
  toLocation: string | null
  performedBy: string
  performedAt: string
  deviceId: string | null
  notes: string | null
}

export interface ContainersQuery {
  status?: ContainerStatus
  type?: ContainerType
  owner?: string
  hasDangerousGoods?: boolean
  location?: string
  createdAfter?: string
  createdBefore?: string
  page?: number
  size?: number
}

export type ContainersPage = PageResponse<ContainerResponse>