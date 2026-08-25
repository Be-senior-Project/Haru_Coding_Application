import {api} from './apiFetch';

export interface UserProfile {
  id: number;
  nickname: string;
  email: string;
  profileImageUrl?: string;
  level: number;
  xp: number;
  streakDays: number;
  totalSolved: number;
  correctCount: number;
  accuracyRate: number;
  preferredLanguage?: string;
  // 티어는 백엔드 UserProfileResponse에 없다. level에서 파생 (theme의 tierFromLevel).
}

export const userApi = {
  getMe: () => api.get<UserProfile>('/api/users/me'),

  updateMe: (data: {nickname?: string; preferredLanguage?: string; fcmToken?: string}) =>
    api.patch<UserProfile>('/api/users/me', data),

  // 회원 탈퇴 — 백엔드 UserController에 DELETE /api/users/me가 아직 없다.
  // 엔드포인트가 추가되면 ProfileScreen의 탈퇴 안내를 이 호출로 되돌리면 됨.
  deleteMe: () => api.del<void>('/api/users/me'),
};
