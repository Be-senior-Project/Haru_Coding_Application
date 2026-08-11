import {api} from './apiFetch';
import type {Problem, ProblemType} from '../types/problem';

// 백엔드 ProblemResponse 와 동일 (= 프론트 공용 Problem 모델)
// 단, 풀이용 조회(GET)는 answer/explanation이 비어 있을 수 있음
export type ProblemResponse = Problem;

export interface AttemptResult {
  problemId: number;
  correct: boolean;
  correctAnswer: string | string[];
  explanation: string;
  xpEarned: number;
  currentStreak: number;
}

export interface ProblemListParams {
  category?: string;
  difficulty?: number;
  type?: ProblemType;
  language?: string;
  limit?: number;
}

function buildQuery(params: Record<string, string | number | undefined>): string {
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== '')
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&');
  return qs ? `?${qs}` : '';
}

export const problemApi = {
  // 문제 은행 목록 (공개)
  list: (params: ProblemListParams = {}) =>
    api.get<Problem[]>(
      `/api/problems${buildQuery({
        category: params.category,
        difficulty: params.difficulty,
        type: params.type,
        language: params.language,
        limit: params.limit,
      })}`,
      false,
    ),

  // 개별 문제 조회 (풀이용, 정답 미포함, 공개)
  get: (id: number) => api.get<Problem>(`/api/problems/${id}`, false),

  // 풀이 제출 → 채점 + 기록 (인증 필요)
  attempt: (id: number, answer: unknown, timeSpentSec?: number) =>
    api.post<AttemptResult>(`/api/problems/${id}/attempt`, {answer, timeSpentSec}),

  // 세트 시작 (인증 필요) → 안 푼 DB 문제 우선, 없으면 즉석 생성. 문제 배열(정답 숨김) 반환
  startSet: (count = 3) =>
    api.post<Problem[]>(`/api/problems/start-set?count=${count}`, {}),
};
