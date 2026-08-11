// 백엔드 problems 스키마와 1:1로 맞춘 프론트 공용 문제 모델

import type {Colors} from '../theme/ThemeContext';

export type ProblemType = 'IMPLEMENTATION' | 'DEBUGGING' | 'FILL_IN_THE_BLANK';

export interface IOExample {
  input: string;
  output: string;
}

export interface Problem {
  id: number;
  setId?: string | null;          // seed의 "Set Id" (예: SET-BI-01)
  type: ProblemType;
  category: string;               // 예: Basic/Introductory
  subcategory?: string | null;    // 예: DFS/BFS (없을 수 있음)
  difficulty: number;             // 0 | 1 | 2
  language: string;               // Python | Java | C++
  title: string;
  description: string;
  constraints?: string[];
  ioExample?: IOExample | null;   // {input, output}
  codeSkeleton?: string | null;
  // 정답: 구현/디버깅은 코드 문자열, 빈칸은 문자열 배열
  answer: string | string[];
  explanation: string;
  conceptExplanation?: string | null;
}

// ── 표시용 라벨/색상 ────────────────────────────────────────────
export const TYPE_LABEL: Record<ProblemType, string> = {
  IMPLEMENTATION: '구현',
  DEBUGGING: '디버깅',
  FILL_IN_THE_BLANK: '빈칸 채우기',
};

export const DIFFICULTY_LABEL: Record<number, string> = {
  0: '기초',
  1: '중급',
  2: '고급',
};

export function difficultyLabel(d: number): string {
  return DIFFICULTY_LABEL[d] ?? '기타';
}

// 난이도 색은 테마 토큰에서 가져온다 (기초=성공, 중급=주의, 고급=위험).
export function difficultyColor(d: number, c: Colors): string {
  return [c.success, c.warning, c.danger][d] ?? c.subText;
}

// 배지 배경용 연한 짝
export function difficultySoft(d: number, c: Colors): string {
  return [c.successSoft, c.warningSoft, c.dangerSoft][d] ?? c.border;
}

// CodeBlock이 지원하는 언어로 매핑 (Java/C++는 우선 javascript 토크나이저로 폴백)
export function toCodeLang(language?: string): 'python' | 'javascript' {
  return language?.toLowerCase().startsWith('py') ? 'python' : 'javascript';
}
