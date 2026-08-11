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

export const statsApi = {
  getMyStats: () => api.get<StatsData>('/api/stats/me'),
};
