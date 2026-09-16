import { get, post, put } from './http';
import type {
  AppointmentListResponse,
  AppointmentsQuery,
  AppointmentResponse,
  BookAppointmentRequest,
  CancelAppointmentRequest,
  CheckInRequest,
  CheckInResponse,
  CheckOutRequest,
  CheckOutResponse,
  ConfirmAppointmentRequest,
  PreviewAppointmentRequest,
  PreviewResponse,
  QuotaListResponse,
  QuotaQuery,
  AdjustQuotaRequest,
  AdjustQuotaResponse,
  QueueView,
  UpdateAppointmentRequest,
} from '../types/gate';

export const gateApi = {
  async listAppointments(query: AppointmentsQuery): Promise<AppointmentListResponse> {
    return get<AppointmentListResponse>(
      '/v1/appointments',
      query as unknown as Record<string, unknown>,
    );
  },
  async getAppointment(id: string): Promise<AppointmentResponse> {
    return get<AppointmentResponse>(`/v1/appointments/${id}`);
  },
  async bookAppointment(req: BookAppointmentRequest): Promise<AppointmentResponse> {
    return post<AppointmentResponse>('/v1/appointments', req);
  },
  async previewAppointment(req: PreviewAppointmentRequest): Promise<PreviewResponse> {
    return post<PreviewResponse>('/v1/appointments/preview', req);
  },
  async confirmAppointment(id: string, req: ConfirmAppointmentRequest): Promise<AppointmentResponse> {
    return post<AppointmentResponse>(`/v1/appointments/${id}/confirm`, req);
  },
  async cancelAppointment(id: string, req: CancelAppointmentRequest): Promise<AppointmentResponse> {
    return post<AppointmentResponse>(`/v1/appointments/${id}/cancel`, req);
  },
  async updateAppointment(id: string, req: UpdateAppointmentRequest): Promise<AppointmentResponse> {
    return put<AppointmentResponse>(`/v1/appointments/${id}`, req);
  },
  async checkIn(req: CheckInRequest): Promise<CheckInResponse> {
    return post<CheckInResponse>('/v1/gate/check-in', req);
  },
  async checkOut(req: CheckOutRequest): Promise<CheckOutResponse> {
    return post<CheckOutResponse>('/v1/gate/check-out', req);
  },
  async listQuotas(query: QuotaQuery = {}): Promise<QuotaListResponse> {
    return get<QuotaListResponse>('/v1/quotas', query as unknown as Record<string, unknown>);
  },
  async adjustQuota(id: string, req: AdjustQuotaRequest): Promise<AdjustQuotaResponse> {
    return post<AdjustQuotaResponse>(`/v1/quotas/${id}/adjust`, req);
  },
  async queueView(): Promise<QueueView> {
    return get<QueueView>('/v1/gate/queue');
  },
};