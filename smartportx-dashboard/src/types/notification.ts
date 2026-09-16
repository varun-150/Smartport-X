export type NotificationCategory =
  | 'YARD_UPDATE' | 'GATE_EVENT' | 'VESSEL_UPDATE' | 'CONTAINER_EVENT'
  | 'APPOINTMENT' | 'ALERT' | 'CUSTOMS' | 'IOT';

export type NotificationSeverity = 'INFO' | 'WARNING' | 'ERROR' | 'CRITICAL';

export type DigestFrequency = 'DAILY' | 'WEEKLY';

export interface QuietHours {
  enabled: boolean;
  start: string;
  end: string;
  timezone: string;
}

export interface NotificationPreferences {
  id: string;
  userId: string;
  emailEnabled: boolean;
  emailAddress: string;
  smsEnabled: boolean;
  smsNumber: string;
  websocketEnabled: boolean;
  pushEnabled: boolean;
  categories: Record<string, boolean>;
  quietHours: QuietHours;
  digestEnabled: boolean;
  digestFrequency: DigestFrequency;
  createdAt: string;
  updatedAt: string;
}

export interface NotificationPreferencesDto {
  emailEnabled: boolean;
  emailAddress: string;
  smsEnabled: boolean;
  smsNumber: string;
  websocketEnabled: boolean;
  pushEnabled: boolean;
  categories: Record<string, boolean>;
  quietHours: QuietHours;
  digestEnabled: boolean;
  digestFrequency: DigestFrequency;
}

export interface NotificationPayload {
  topic: string;
  notificationId: string;
  category: NotificationCategory;
  severity: NotificationSeverity;
  title: string;
  message: string;
  timestamp: string;
  actionUrl: string | null;
  data: Record<string, unknown> | null;
}

export const SEVERITY_RANK: Record<NotificationSeverity, number> = {
  INFO: 0,
  WARNING: 1,
  ERROR: 2,
  CRITICAL: 3,
};