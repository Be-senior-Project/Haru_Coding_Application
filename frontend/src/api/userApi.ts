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
  tier?: string; // 리그 티어 (BRONZE/SILVER/GOLD/PLATINUM/DIAMOND)
}

export const userApi = {
  getMe: () => api.get<UserProfile>('/api/users/me'),

  updateMe: (data: {nickname?: string; preferredLanguage?: string; fcmToken?: string}) =>
    api.patch<UserProfile>('/api/users/me', data),

  // 회원 탈퇴
  deleteMe: () => api.del<void>('/api/users/me'),

  /**
   * 온보딩 답변 저장. 가입이 확정된 직후에만 호출한다.
   * 추천 난이도는 서버가 답변에서 계산하므로 보내지 않는다.
   */
  saveOnboarding: (codingLevel: string, cotePrepared: boolean) =>
    api.post<void>('/api/users/me/onboarding', {codingLevel, cotePrepared}),
};
