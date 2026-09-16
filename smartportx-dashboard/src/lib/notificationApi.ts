import { get, put } from './http';
import type { NotificationPreferences, NotificationPreferencesDto } from '../types/notification';

export function getPreferences(): Promise<NotificationPreferences> {
  return get<NotificationPreferences>('/v1/notifications/preferences');
}

export function updatePreferences(
  request: NotificationPreferencesDto,
): Promise<NotificationPreferences> {
  return put<NotificationPreferences>('/v1/notifications/preferences', request);
}