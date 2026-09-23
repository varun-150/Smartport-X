// Gate / appointment API client — paths verified against gate-service controllers.

import { get, post, put } from '../lib/http'
import type {
  AppointmentQuery,
  AppointmentResponse,
  BookAppointmentRequest,
  CancelAppointmentRequest,
  ConfirmAppointmentRequest,
  PageResponse,
  PreviewAppointmentRequest,
  PreviewAppointmentResponse,
  UpdateAppointmentRequest,
} from '../types'

export const gateApi = {
  list: (query: AppointmentQuery = {}) =>
    get<PageResponse<AppointmentResponse>>('/v1/appointments', {
      ...(query.status ? { 'filter[status]': query.status } : {}),
      ...(query.containerNumber ? { 'filter[containerNumber]': query.containerNumber } : {}),
      ...(query.dateFrom ? { 'filter[dateFrom]': query.dateFrom } : {}),
      ...(query.dateTo ? { 'filter[dateTo]': query.dateTo } : {}),
      ...(query.laneId ? { 'filter[laneId]': query.laneId } : {}),
      ...(query.page != null ? { page: query.page } : {}),
      ...(query.size != null ? { size: query.size } : {}),
    }),

  get: (id: string) => get<AppointmentResponse>(`/v1/appointments/${id}`),

  book: (req: BookAppointmentRequest) => post<AppointmentResponse>('/v1/appointments', req),

  update: (id: string, req: UpdateAppointmentRequest) =>
    put<AppointmentResponse>(`/v1/appointments/${id}`, req),

  preview: (req: PreviewAppointmentRequest) =>
    post<PreviewAppointmentResponse>('/v1/appointments/preview', req),

  confirm: (id: string, req: ConfirmAppointmentRequest) =>
    post<AppointmentResponse>(`/v1/appointments/${id}/confirm`, req),

  cancel: (id: string, req: CancelAppointmentRequest) =>
    post<AppointmentResponse>(`/v1/appointments/${id}/cancel`, req),
}