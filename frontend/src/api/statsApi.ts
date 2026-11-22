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

// 유형별·난이도별 정답률 한 줄. key는 유형이면 IMPLEMENTATION·DEBUGGING·FILL_IN_THE_BLANK, 난이도면 '0'·'1'·'2'
export interface BreakdownStat {
  key: string;
  totalSolved: number;
  correctCount: number;
  accuracyRate: number;
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
  // 선택한 기간(주간·월간·연간)의 날짜별 처음 푼 문제 수. 키는 한국 시간 yyyy-MM-dd, 푼 날만 들어 있다. 전체 탭이면 빈 객체
  dailyActivity?: Record<string, number>;
  typeStats?: BreakdownStat[];        // 유형별 정답률(키 순서 고정, 안 푼 유형도 0으로 포함)
  difficultyStats?: BreakdownStat[];  // 난이도별 정답률(입문·초급·중급 순)
  recentRecords: RecentRecord[];
}

export type StatsPeriod = 'ALL' | 'WEEK' | 'MONTH' | 'YEAR';

export const statsApi = {
  getMyStats: (period: StatsPeriod = 'ALL') =>
    api.get<StatsData>(`/api/stats/me?period=${period}`),
};
