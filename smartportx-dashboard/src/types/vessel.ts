// Vessel contracts (vessel-service).

import type { ListResponse } from './api'

export type VesselType =
  | 'CONTAINER'
  | 'BULK'
  | 'TANKER'
  | 'GENERAL'
  | 'ROLL_ON_ROLL_OFF'

export type VesselStatus = 'SCHEDULED' | 'IN_TRANSIT' | 'ARRIVED' | 'WORKING' | 'DEPARTED' | 'CANCELLED'

export type ScheduleStatus = 'PLANNED' | 'CALLED' | 'BERTHED' | 'DEPARTED' | 'VOID'

export type DischargePlanStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED'

export type DischargeContainerStatus = 'PLANNED' | 'DISCHARGED' | 'IN_TRANSIT' | 'STACKED' | 'MISROUTED'

export interface RegisterVesselRequest {
  vesselName: string
  imoNumber: string
  vesselType: VesselType
  capacityTeu: number
  nationality?: string
}

export interface VesselResponse {
  id: string
  vesselName: string
  imoNumber: string
  vesselType: VesselType
  capacityTeu: number
  currentStatus: VesselStatus
  registeredAt: string
  updatedAt: string
}

export interface VesselSchedule {
  id: string
  vesselId: string
  voyageNumber: string
  berthNumber: string
  eta: string
  etd: string
  status: ScheduleStatus
}

export interface DischargePlanContainer {
  id: string
  vesselId: string
  containerNumber: string
  status: DischargeContainerStatus
  targetBlockCode: string | null
  stackedAt: string | null
}

export interface VesselDetail {
  vessel: VesselResponse
  schedules: VesselSchedule[]
  dischargePlans: DischargePlanContainer[]
}

export interface AddScheduleRequest {
  voyageNumber: string
  berthNumber: string
  eta: string
  etd: string
  priority?: number
}

export interface VesselQuery {
  status?: VesselStatus
  vesselType?: VesselType
  search?: string
}

export type VesselsPage = ListResponse<VesselResponse>