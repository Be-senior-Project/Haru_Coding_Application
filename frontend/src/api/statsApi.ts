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
  totalSolved: number;
  correctCount: number;
  accuracyRate: number;
  currentStreak: number;
  weeklyActivity: number[];
  categoryStats: CategoryStat[];
  recentRecords: RecentRecord[];
}

export type StatsPeriod = 'ALL' | 'WEEK' | 'MONTH' | 'YEAR';

export const statsApi = {
  getMyStats: (period: StatsPeriod = 'ALL') =>
    api.get<StatsData>(`/api/stats/me?period=${period}`),
};
