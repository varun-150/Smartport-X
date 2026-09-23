// Notification contracts (notification-service).

export type NotificationCategory =
  | 'YARD_UPDATE'
  | 'GATE_EVENT'
  | 'VESSEL_UPDATE'
  | 'CONTAINER_EVENT'
  | 'APPOINTMENT'
  | 'ALERT'
  | 'CUSTOMS'
  | 'IOT'

export type NotificationSeverity = 'INFO' | 'WARNING' | 'ERROR' | 'CRITICAL'

export type DigestFrequency = 'DAILY' | 'WEEKLY'

export interface QuietHours {
  start: string
  end: string
}

export interface NotificationPreferences {
  userId: string
  emailEnabled: boolean
  smsEnabled: boolean
  digestFrequency: DigestFrequency
  quietHours: QuietHours
  categories: Record<NotificationCategory, boolean>
}

export type NotificationPreferencesDto = Partial<Omit<NotificationPreferences, 'userId'>> & {
  categories?: Partial<Record<NotificationCategory, boolean>>
}

export interface NotificationPayload {
  id: string
  userId: string
  title: string
  message: string
  category: NotificationCategory
  severity: NotificationSeverity
  read: boolean
  createdAt: string
}

export const SEVERITY_RANK: Record<NotificationSeverity, number> = {
  CRITICAL: 4,
  ERROR: 3,
  WARNING: 2,
  INFO: 1,
}