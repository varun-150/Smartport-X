import type { Pagination } from './api';

export type AppointmentType = 'PICKUP' | 'DROP_OFF' | 'BOTH';

export type AppointmentStatus =
  | 'PENDING' | 'CONFIRMED' | 'CHECKED_IN' | 'IN_PROGRESS'
  | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';

export interface BookAppointmentRequest {
  truckingCompany: string;
  truckLicensePlate: string;
  driverName: string;
  driverLicense: string;
  appointmentType: AppointmentType;
  containerNumber: string;
  preferredTimeSlot: string;
  durationMinutes: number;
  gateLane: string | null;
  dangerousGoods: boolean;
  specialRequirements: string[];
  notes: string | null;
}

export interface PreviewAppointmentRequest {
  appointmentType: AppointmentType;
  preferredTimeSlot: string;
  containerNumber: string;
}

export interface PreviewResponse {
  appointmentType: string;
  available: boolean;
  suggestedSlot: string | null;
  confirmationNumber: string | null;
  availableSlots: number;
  estimatedCost: number;
}

export interface ConfirmAppointmentRequest {
  confirmationCode: string;
  verifiedBy: string | null;
}

export interface CancelAppointmentRequest {
  reason: string;
  cancelledBy: string | null;
}

export interface UpdateAppointmentRequest {
  preferredTimeSlot?: string;
  truckLicensePlate?: string;
  driverName?: string;
  driverLicense?: string;
  containerNumber?: string;
  gateLane?: string;
  specialRequirements?: string[];
  notes?: string;
}

export interface AppointmentResponse {
  id: string;
  appointmentId: string;
  confirmationNumber: string;
  truckingCompany: string;
  truckLicensePlate: string;
  driverName: string;
  driverLicense: string;
  appointmentType: string;
  containerNumber: string;
  preferredTimeSlot: string;
  durationMinutes: number;
  status: string;
  gateLane: string | null;
  yardLocation: string | null;
  specialRequirements: string[];
  documents: string[];
  notes: string | null;
  estimatedCost: number;
  availableSlots: number;
  createdAt: string;
  updatedAt: string;
}

export interface AppointmentListResponse {
  data: AppointmentResponse[];
  pagination: Pagination;
}

export interface AppointmentsQuery {
  limit?: number;
  cursor?: string;
  sort?: 'asc' | 'desc';
  'filter[status]'?: AppointmentStatus;
  'filter[type]'?: AppointmentType;
  'filter[company]'?: string;
  'filter[date]'?: string;
}

export interface CheckInRequest {
  appointmentId: string;
  truckLicensePlate: string;
  driverName: string;
  driverLicense: string;
  containerNumber: string;
  sealNumber: string | null;
  truckWeight: number | null;
  documents: string[];
  notes: string | null;
}

export interface CheckInResponse {
  transactionId: string;
  appointmentId: string;
  confirmationNumber: string;
  status: string;
  gateLane: string;
  checkInTime: string;
  yardLocation: string;
  estimatedServiceTime: string;
  documents: DocumentResponse[];
  instructions: string;
}

export interface CheckOutRequest {
  transactionId: string;
  appointmentId: string;
  truckLicensePlate: string;
  driverName: string;
  driverLicense: string;
  containerNumber: string;
  sealIntact: boolean;
  newSealNumber: string | null;
  truckWeight: number | null;
  notes: string | null;
}

export interface CheckOutResponse {
  transactionId: string;
  appointmentId: string;
  confirmationNumber: string;
  status: string;
  eirNumber: string;
  checkInTime: string;
  checkOutTime: string;
  gateLane: string;
  yardLocation: string;
  verifiedBy: string;
}

export interface DocumentResponse {
  id: string;
  name: string;
  status: string;
  verifiedAt: string | null;
  verifiedBy: string | null;
}

export interface QuotaResponse {
  quotaId: string;
  appointmentDate: string;
  timeSlotStart: string;
  timeSlotEnd: string;
  gateLane: string;
  appointmentType: AppointmentType;
  totalAppointments: number;
  availableSlots: number;
  reservedCount: number;
  dynamic: boolean;
  adjustedBy: string | null;
  adjustmentReason: string | null;
  updatedAt: string;
}

export interface QuotaListResponse {
  data: QuotaResponse[];
}

export interface AdjustQuotaRequest {
  newMaxAppointments: number;
  adjustedBy: string | null;
  reason: string;
}

export interface AdjustQuotaResponse {
  quota: QuotaResponse;
}

export interface QuotaQuery {
  date?: string;
  gateLane?: string;
}

export interface QueueView {
  waitingTrucks: number;
  pickupCount: number;
  dropoffCount: number;
  averageWaitMinutes: number;
  byGateLane: LaneStatus[];
  upcomingAppointments: AppointmentView[];
  operator: string;
  instructions: string[];
}

export interface LaneStatus {
  lane: string;
  status: string;
  currentQueue: number;
  averageWait: number;
  estimatedWaitMinutes: number;
}

export interface AppointmentView {
  confirmationNumber: string;
  type: string;
  truckLicensePlate: string;
  containerNumber: string;
  preferredTimeSlot: string;
}