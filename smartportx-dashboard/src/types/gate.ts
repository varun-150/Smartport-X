// Gate / appointment contracts (gate-service).

export type AppointmentType = 'PICKUP' | 'DROP_OFF' | 'BOTH'

export type AppointmentStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW'

export interface BookAppointmentRequest {
  containerNumber: string
  appointmentType: AppointmentType
  scheduledFor: string
  laneId?: string
  truckCompany?: string
  plateNumber?: string
  driverName?: string
}

export interface PreviewAppointmentRequest {
  containerNumber: string
  appointmentType: AppointmentType
}

export interface PreviewAppointmentResponse {
  eligible: boolean
  availableSlots: number
  message: string | null
  earliestSlot: string | null
}

export interface ConfirmAppointmentRequest {
  confirmationCode: string
  verifiedBy: string
}

export interface CancelAppointmentRequest {
  reason: string
  cancelledBy: string
}

export interface UpdateAppointmentRequest {
  scheduledFor?: string
  laneId?: string
  truckCompany?: string
  plateNumber?: string
  driverName?: string
}

export interface AppointmentResponse {
  id: string
  appointmentNumber: string
  containerNumber: string
  appointmentType: AppointmentType
  status: AppointmentStatus
  scheduledFor: string
  confirmedAt: string | null
  checkedInAt: string | null
  completedAt: string | null
  cancelledAt: string | null
  cancellationReason: string | null
  laneId: string | null
  truckCompany: string | null
  plateNumber: string | null
  driverName: string | null
  createdBy: string
  createdAt: string
}

export interface AppointmentQuery {
  status?: AppointmentStatus
  containerNumber?: string
  dateFrom?: string
  dateTo?: string
  laneId?: string
  page?: number
  size?: number
}