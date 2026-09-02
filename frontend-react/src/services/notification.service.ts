import axios, { type InternalAxiosRequestConfig } from 'axios';
import { tokenStorage } from '../lib/api';
import { REALTIME_URL } from '../lib/realtimeSocket';
import type {
  Notification,
  NotificationListResponse,
  NotificationPreferences,
} from '../types/notification';

/**
 * BUG 10 (2026-06-08) — Client REST des notifications. Comme le chat,
 * les endpoints sont exposes par le realtime-service Node.js sur
 * VITE_REALTIME_URL et non par le gateway Java.
 */
const realtimeApi = axios.create({
  baseURL: `${REALTIME_URL}/api/v1`,
  headers: { 'Content-Type': 'application/json' },
});

realtimeApi.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStorage.getAccessToken();
  if (token && !config.headers.has('Authorization')) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

export const notificationService = {
  async list(opts: { unread?: boolean; limit?: number } = {}): Promise<NotificationListResponse> {
    const { data } = await realtimeApi.get<NotificationListResponse>('/notifications', {
      params: {
        unread: opts.unread ? 'true' : undefined,
        limit: opts.limit ?? 50,
      },
    });
    return data;
  },

  async unreadCount(): Promise<number> {
    const { data } = await realtimeApi.get<{ count: number }>('/notifications/unread-count');
    return data.count;
  },

  async markRead(id: string): Promise<void> {
    await realtimeApi.patch(`/notifications/${id}/read`);
  },

  async markAllRead(): Promise<number> {
    const { data } = await realtimeApi.patch<{ updated: number }>('/notifications/read-all');
    return data.updated;
  },

  async getPreferences(): Promise<NotificationPreferences> {
    const { data } = await realtimeApi.get<NotificationPreferences>('/notifications/preferences');
    return data;
  },

  async setPreferences(prefs: Record<string, boolean>): Promise<void> {
    await realtimeApi.patch('/notifications/preferences', { preferences: prefs });
  },

  /** Dev/E2E uniquement — declenche un push notification:received sur le user courant. */
  async selfTest(payload: Partial<Pick<Notification, 'type' | 'title' | 'message' | 'actionUrl'>> = {}): Promise<Notification> {
    const { data } = await realtimeApi.post<Notification>('/notifications/self-test', payload);
    return data;
  },
};
