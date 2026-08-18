import {api} from './apiFetch';
import type {ProblemType} from '../types/problem';

/**
 * 오답노트 목록 1건.
 * 백엔드 WrongNoteResponse와 1:1. 별도 테이블이 아니라
 * user_problem_records의 오답 기록을 문제 단위로 중복 제거해 내려준다.
 */
export interface WrongNote {
  recordId: number;
  problemId: number;
  title: string;
  category: string;
  subcategory?: string | null;
  difficulty: number; // 0 | 1 | 2
  language: string; // Python | Java | C++
  type: ProblemType;
  userAnswer?: unknown; // 마지막으로 제출했던 오답
  timeSpentSec?: number | null;
  solvedAt: string; // ISO
}

export const wrongNoteApi = {
  /** 틀린 문제 목록(최신순). 같은 문제는 가장 최근 오답 1건만 온다. */
  list: () => api.get<WrongNote[]>('/api/wrong-notes'),
};
