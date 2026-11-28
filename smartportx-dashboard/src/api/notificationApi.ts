// Notification API client — paths verified against notification-service controllers.

import { get, put } from '../lib/http'
import type { NotificationPreferences, NotificationPreferencesDto } from '../types'

export const notificationApi = {
  preferences: () => get<NotificationPreferences>('/v1/notifications/preferences'),

  updatePreferences: (req: NotificationPreferencesDto) =>
    put<NotificationPreferences>('/v1/notifications/preferences', req),
}