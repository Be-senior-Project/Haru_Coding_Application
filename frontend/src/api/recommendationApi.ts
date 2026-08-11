import {api} from './apiFetch';
import type {ProblemType} from '../types/problem';

export interface AreaStat {
  area: string;
  attempts: number;
  correct: number;
  accuracyRate: number; // 0~100
}

export interface CompetencySummary {
  totalSolved: number;
  accuracyRate: number;   // 0~100
  estimatedLevel: number; // 0 | 1 | 2
  weakAreas: AreaStat[];
  strongAreas: AreaStat[];
}

export interface RecommendedProblem {
  problemId: number;
  title: string;
  category: string;
  subcategory?: string | null;
  difficulty: number; // 0 | 1 | 2
  type: ProblemType;
  language: string;
  similarityScore: number;     // 0~1
  matchedWeakness?: string | null;
  review: boolean;
  reason: string;
}

export interface RecommendationResponse {
  userId: number;
  generatedAt: string;
  strategy: string; // SIMILARITY | PERSONALIZED | COLD_START
  summary: CompetencySummary;
  recommendations: RecommendedProblem[];
}

export const recommendationApi = {
  // 로그인 유저 역량 기반 추천 (인증 필요)
  get: (limit = 5) =>
    api.get<RecommendationResponse>(`/api/recommendations?limit=${limit}`),
};
