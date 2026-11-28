// Vessel API client — paths verified against vessel-service controllers.
// The detail endpoint returns a flat VesselResponse, while the dashboard's
// VesselDetailPage expects a vessel + schedules + dischargePlans aggregate.
// The aggregate is assembled here from the three documented endpoints.

import { del, get, patch, post, put } from '../lib/http'
import type {
  AddScheduleRequest,
  DischargePlanContainer,
  RegisterVesselRequest,
  VesselDetail,
  VesselQuery,
  VesselResponse,
  VesselSchedule,
  VesselsPage,
} from '../types'

// The detail endpoint returns the canonical VesselResponse fields; older
// deployments may additionally expose legacy aliases. Keep the canonical
// fields in the raw type so the normalisation below can read them.
type RawVessel = VesselResponse & {
  teuCapacity?: number
  createdAt?: string
}

type ItemEnvelope<T> = { items: T[] }

export const vesselApi = {
  list: (query: VesselQuery = {}) =>
    get<VesselsPage>('/v1/vessels', {
      ...(query.status ? { 'filter[status]': query.status } : {}),
      ...(query.vesselType ? { 'filter[vesselType]': query.vesselType } : {}),
      ...(query.search ? { 'filter[search]': query.search } : {}),
    }),

  register: (req: RegisterVesselRequest) => post<VesselResponse>('/v1/vessels', req),

  get: async (id: string): Promise<VesselDetail> => {
    const [raw, schedules, plans] = await Promise.all([
      get<RawVessel>(`/v1/vessels/${id}`),
      get<ItemEnvelope<VesselSchedule>>(`/v1/vessels/${id}/schedules`),
      get<ItemEnvelope<DischargePlanContainer>>(`/v1/vessels/${id}/discharge-plans`),
    ])
    const vessel: VesselResponse = {
      ...raw,
      capacityTeu: raw.capacityTeu ?? raw.teuCapacity ?? 0,
      currentStatus: raw.currentStatus ?? 'SCHEDULED',
      registeredAt: raw.registeredAt ?? raw.createdAt ?? '',
    }
    return {
      vessel,
      schedules: schedules?.items ?? [],
      dischargePlans: plans?.items ?? [],
    }
  },

  update: (id: string, req: RegisterVesselRequest) => put<VesselResponse>(`/v1/vessels/${id}`, req),

  remove: (id: string) => del<void>(`/v1/vessels/${id}`),

  addSchedule: (vesselId: string, req: AddScheduleRequest) =>
    post<VesselSchedule>(`/v1/vessels/${vesselId}/schedules`, req),

  schedules: (vesselId: string) =>
    get<VesselSchedule[]>(`/v1/vessels/${vesselId}/schedules`),

  updateScheduleStatus: (scheduleId: string) =>
    patch<VesselSchedule>(`/v1/schedules/${scheduleId}`),
}
