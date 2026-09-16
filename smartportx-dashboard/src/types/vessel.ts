import type { ListResponse } from './api';

export type VesselType =
  | 'CONTAINER'
  | 'BULK'
  | 'TANKER'
  | 'GENERAL'
  | 'ROLL_ON_ROLL_OFF';

export type VesselStatus =
  | 'SCHEDULED'
  | 'IN_TRANSIT'
  | 'ARRIVED'
  | 'WORKING'
  | 'DEPARTED'
  | 'CANCELLED';

export type ScheduleStatus =
  | 'SCHEDULED'
  | 'IN_TRANSIT'
  | 'ARRIVED'
  | 'WORKING'
  | 'DEPARTED'
  | 'CANCELLED';

export type SchedulePriority = 'HIGH' | 'MEDIUM' | 'LOW';

export type DischargePlanStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED';

export type DischargeContainerStatus =
  | 'PLANNED'
  | 'DISCHARGED'
  | 'IN_TRANSIT'
  | 'STACKED'
  | 'MISROUTED';

export interface RegisterVesselRequest {
  imoNumber: string;
  vesselName: string;
  vesselType: VesselType;
  lengthM: number;
  beamM: number;
  draftM: number;
  teuCapacity: number | null;
  grossTonnage: number | null;
  flag: string | null;
  operator: string | null;
  callSign: string | null;
}

export interface VesselResponse {
  id: string;
  imoNumber: string;
  vesselName: string;
  vesselType: VesselType;
  lengthM: number;
  beamM: number;
  draftM: number;
  teuCapacity: number | null;
  grossTonnage: number | null;
  flag: string | null;
  operator: string | null;
  callSign: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface VesselDetailResponse extends VesselResponse {
  currentStatus: string;
}

export type VesselsPage = ListResponse<VesselResponse>;

export interface AddScheduleRequest {
  voyageNumber: string;
  berthNumber: string;
  eta: string;
  etd: string;
  priority: SchedulePriority;
  portOfCall: string | null;
  nextPort: string | null;
  estimatedTeUs: number | null;
}

export interface UpdateScheduleStatusRequest {
  status: ScheduleStatus;
}

export interface ScheduleResponse {
  id: string;
  vesselId: string;
  voyageNumber: string;
  berthNumber: string;
  priority: SchedulePriority;
  status: ScheduleStatus;
  eta: string;
  etd: string;
  portOfCall: string | null;
  nextPort: string | null;
  estimatedTeUs: number | null;
  actualTeUs: number | null;
  arrivedAt: string | null;
  departedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export type ScheduleListResponse = ListResponse<ScheduleResponse>;

export interface ContainerPlanRequest {
  containerNumber: string;
  containerType: string;
  weightKg: number | null;
  vesselPosition: string;
  yardTarget: string | null;
  priority: SchedulePriority;
  dangerousGoods: boolean;
  reefer: boolean;
}

export interface CreatePlanRequest {
  voyageNumber: string;
  containers: ContainerPlanRequest[];
}

export interface ProgressUpdateRequest {
  containerNumber: string;
  status: DischargeContainerStatus;
  actualYardPosition: string | null;
  dischargedAt: string | null;
  stackedAt: string | null;
  equipmentId?: string | null;
  operator?: string | null;
  notes?: string | null;
}

export interface PlanResponse {
  id: string;
  vesselId: string;
  voyageNumber: string;
  status: DischargePlanStatus;
  totalContainers: number;
  totalTeUs: number;
  estimatedDurationMinutes: number;
  createdAt: string;
  progress: PlanProgress;
  containers: DischargeContainerResponse[];
}

export interface PlanProgress {
  totalContainers: number;
  dischargedCount: number;
  inTransitCount: number;
  stackedCount: number;
  pendingCount: number;
  completionPercentage: number;
  estimatedCompletion: string;
}

export interface DischargeContainerResponse {
  containerNumber: string;
  containerType: string;
  weightKg: number | null;
  vesselPosition: string | null;
  yardTarget: string | null;
  status: DischargeContainerStatus;
  actualYardPosition: string | null;
  dischargedAt: string | null;
  stackedAt: string | null;
  operator: string | null;
}

export interface ProgressResponse {
  containers: DischargeContainerResponse[];
  progress: PlanProgress;
}

export type PlanListResponse = ListResponse<PlanResponse>;