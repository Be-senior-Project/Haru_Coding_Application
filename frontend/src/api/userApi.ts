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
  dailyGoalCount: number;
  difficultyLevel?: string | null; // null/미설정 = 자동 추천, 아니면 L1~L5
  // 티어는 백엔드 UserProfileResponse에 없다. level에서 파생 (theme의 tierFromLevel).
}

export const userApi = {
  getMe: () => api.get<UserProfile>('/api/users/me'),

  updateMe: (data: {
    nickname?: string;
    preferredLanguage?: string;
    fcmToken?: string;
    dailyGoalCount?: number;
    difficultyLevel?: string; // "AUTO" | "L1".."L5"
  }) => api.patch<UserProfile>('/api/users/me', data),

  // 회원 탈퇴 — 백엔드 UserController에 DELETE /api/users/me가 아직 없다.
  // 엔드포인트가 추가되면 ProfileScreen의 탈퇴 안내를 이 호출로 되돌리면 됨.
  deleteMe: () => api.del<void>('/api/users/me'),

  /**
   * 온보딩 답변 저장. 가입이 확정된 직후에만 호출한다.
   * 추천 난이도는 서버가 답변에서 계산하므로 보내지 않는다.
   */
  saveOnboarding: (codingLevel: string, cotePrepared: boolean) =>
    api.post<void>('/api/users/me/onboarding', {codingLevel, cotePrepared}),
};
