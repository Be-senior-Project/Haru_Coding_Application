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

// 서버 기준(프로그래머스 Lv.0~2)과 같은 이름. 서버는 0·1·2를 입문·초급·중급으로 쓴다(DTO·세트 난이도 변환 참고).
export const DIFFICULTY_LABEL: Record<number, string> = {
  0: '입문',
  1: '초급',
  2: '중급',
};

export function difficultyLabel(d: number): string {
  return DIFFICULTY_LABEL[d] ?? '기타';
}

// 난이도 색은 테마 토큰에서 가져온다 (입문=성공, 초급=주의, 중급=위험).
export function difficultyColor(d: number, c: Colors): string {
  return [c.success, c.warning, c.danger][d] ?? c.subText;
}

// 배지 배경용 연한 짝
export function difficultySoft(d: number, c: Colors): string {
  return [c.successSoft, c.warningSoft, c.dangerSoft][d] ?? c.border;
}

// 서버의 영어 분류명(category/subcategory) → 화면에 보일 한국어 이름. 문제 은행·학습 통계가 같이 쓴다.
export const CATEGORY_LABEL: Record<string, string> = {
  'Basic/Introductory': '기초/입문',
  'Algorithm/Data Structure': '알고리즘',
  'Past Exam Prep': '기출 대비',
  'DFS/BFS': '그래프 탐색',
  'Dynamic Programming': '동적 계획법',
};

export function categoryLabel(s?: string | null): string {
  return s ? CATEGORY_LABEL[s] ?? s : '';
}

// CodeBlock이 지원하는 언어로 매핑 (Java/C++는 우선 javascript 토크나이저로 폴백)
export function toCodeLang(language?: string): 'python' | 'javascript' {
  return language?.toLowerCase().startsWith('py') ? 'python' : 'javascript';
}
