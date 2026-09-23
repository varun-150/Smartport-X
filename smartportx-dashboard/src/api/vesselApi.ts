// Vessel API client — paths verified against vessel-service controllers.

import { del, get, patch, post, put } from '../lib/http'
import type {
  AddScheduleRequest,
  RegisterVesselRequest,
  VesselDetail,
  VesselQuery,
  VesselResponse,
  VesselSchedule,
  VesselsPage,
} from '../types'

export const vesselApi = {
  list: (query: VesselQuery = {}) =>
    get<VesselsPage>('/v1/vessels', {
      ...(query.status ? { 'filter[status]': query.status } : {}),
      ...(query.vesselType ? { 'filter[vesselType]': query.vesselType } : {}),
      ...(query.search ? { 'filter[search]': query.search } : {}),
    }),

  register: (req: RegisterVesselRequest) => post<VesselResponse>('/v1/vessels', req),

  get: (id: string) => get<VesselDetail>(`/v1/vessels/${id}`),

  update: (id: string, req: RegisterVesselRequest) => put<VesselResponse>(`/v1/vessels/${id}`, req),

  remove: (id: string) => del<void>(`/v1/vessels/${id}`),

  addSchedule: (vesselId: string, req: AddScheduleRequest) =>
    post<VesselSchedule>(`/v1/vessels/${vesselId}/schedules`, req),

  schedules: (vesselId: string) =>
    get<VesselSchedule[]>(`/v1/vessels/${vesselId}/schedules`),

  updateScheduleStatus: (scheduleId: string) =>
    patch<VesselSchedule>(`/v1/schedules/${scheduleId}`),
}