import {api} from './apiFetch';

export interface AppNotification {
  id: number;
  title: string;
  body: string;
  read: boolean;
  createdAt: string; // ISO
}

export const notificationApi = {
  /** 알림 목록(최신순). */
  list: () => api.get<AppNotification[]>('/api/notifications'),

  /** 단건 확인 처리. */
  markRead: (id: number) => api.patch<void>(`/api/notifications/${id}/read`, {}),

  /** 전체 확인 처리. */
  markAllRead: () => api.patch<void>('/api/notifications/read-all', {}),
};
