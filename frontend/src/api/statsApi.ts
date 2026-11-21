import {api} from './apiFetch';

export interface CategoryStat {
  topicId: number;
  topicName: string;
  icon: string;
  totalSolved: number;
  correctCount: number;
  accuracyRate: number;
}

export interface RecentRecord {
  problemId: number;
  problemTitle: string;
  topic: string;
  isCorrect: boolean;
  solvedAt: string;
}

export interface StatsData {
  // 푼 문제 수·정답률은 문제별 첫 제출 기준(같은 문제를 다시 제출해도 늘지 않음)
  totalSolved: number;
  correctCount: number;
  accuracyRate: number;
  currentStreak: number;
  weeklyActivity: number[];       // 이번 주 월~일, 날짜별 처음 푼 문제 수
  categoryStats: CategoryStat[];
  // 약점 영역의 분류명(서버 기준: 3문제 이상 풀고 정답률 70% 미만인 영역 중 가장 낮은 곳). 없으면 null
  weakCategory?: string | null;
  recentRecords: RecentRecord[];
}

export type StatsPeriod = 'ALL' | 'WEEK' | 'MONTH' | 'YEAR';

export const statsApi = {
  getMyStats: (period: StatsPeriod = 'ALL') =>
    api.get<StatsData>(`/api/stats/me?period=${period}`),
};
