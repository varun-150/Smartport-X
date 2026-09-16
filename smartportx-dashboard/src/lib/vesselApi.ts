import { get, post, put, del, patch } from './http';
import {
  type AddScheduleRequest,
  type CreatePlanRequest,
  type ProgressUpdateRequest,
  type PlanListResponse,
  type PlanResponse,
  type RegisterVesselRequest,
  type ScheduleListResponse,
  type ScheduleResponse,
  type UpdateScheduleStatusRequest,
  type VesselDetailResponse,
  type VesselResponse,
  type VesselsPage,
} from '../types/vessel';

export function registerVessel(request: RegisterVesselRequest): Promise<VesselResponse> {
  return post<VesselResponse>('/v1/vessels', request);
}

export function listVessels(): Promise<VesselsPage> {
  return get<VesselsPage>('/v1/vessels');
}

export function getVessel(vesselId: string): Promise<VesselDetailResponse> {
  return get<VesselDetailResponse>(`/v1/vessels/${encodeURIComponent(vesselId)}`);
}

export function updateVessel(vesselId: string, request: RegisterVesselRequest): Promise<VesselResponse> {
  return put<VesselResponse>(`/v1/vessels/${encodeURIComponent(vesselId)}`, request);
}

export function deleteVessel(vesselId: string): Promise<void> {
  return del<void>(`/v1/vessels/${encodeURIComponent(vesselId)}`);
}

export function addSchedule(vesselId: string, request: AddScheduleRequest): Promise<ScheduleResponse> {
  return post<ScheduleResponse>(`/v1/vessels/${encodeURIComponent(vesselId)}/schedules`, request);
}

export function listSchedules(vesselId: string): Promise<ScheduleListResponse> {
  return get<ScheduleListResponse>(`/v1/vessels/${encodeURIComponent(vesselId)}/schedules`);
}

export function updateScheduleStatus(scheduleId: string, request: UpdateScheduleStatusRequest): Promise<ScheduleResponse> {
  return patch<ScheduleResponse>(`/v1/schedules/${encodeURIComponent(scheduleId)}/status`, request);
}

export function inPortSchedules(): Promise<ScheduleListResponse> {
  return get<ScheduleListResponse>('/v1/schedules/in-port');
}

export function createDischargePlan(vesselId: string, request: CreatePlanRequest): Promise<PlanResponse> {
  return post<PlanResponse>(`/v1/vessels/${encodeURIComponent(vesselId)}/discharge-plans`, request);
}

export function listDischargePlans(vesselId: string): Promise<PlanListResponse> {
  return get<PlanListResponse>(`/v1/vessels/${encodeURIComponent(vesselId)}/discharge-plans`);
}

export function getDischargePlan(planId: string): Promise<PlanResponse> {
  return get<PlanResponse>(`/v1/discharge-plans/${encodeURIComponent(planId)}`);
}

export function updateDischargeProgress(planId: string, request: ProgressUpdateRequest): Promise<PlanResponse> {
  return patch<PlanResponse>(`/v1/discharge-plans/${encodeURIComponent(planId)}/progress`, request);
}