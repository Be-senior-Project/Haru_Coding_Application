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
  // 오늘의 세트에서 약점 영역 문제로 출제됐을 때 그 영역(하위 분류, 없으면 분류). 그 밖에는 없음
  weakArea?: string | null;
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

// 표시 이름(입문·초급·중급) → 0·1·2. 서버가 이름으로 주는 곳(온보딩 결과)에서 쓴다. 모르는 이름이면 -1
export function difficultyFromLabel(label: string): number {
  const found = Object.entries(DIFFICULTY_LABEL).find(([, name]) => name === label);
  return found ? Number(found[0]) : -1;
}

// 난이도 색은 테마 토큰에서 가져온다 (입문=성공, 초급=주의, 중급=위험).
export function difficultyColor(d: number, c: Colors): string {
  return [c.success, c.warning, c.danger][d] ?? c.subText;
}

// 배지 배경용 연한 짝
export function difficultySoft(d: number, c: Colors): string {
  return [c.successSoft, c.warningSoft, c.dangerSoft][d] ?? c.border;
}

// 서버의 영어 분류명(category/subcategory) → 화면에 보일 한국어 이름. 문제 은행·학습 통계·문제 풀이가 같이 쓴다.
// 분류 3개와 하위 분류 13개(문제 생성 프롬프트 generation_en.md 기준)를 모두 둔다.
export const CATEGORY_LABEL: Record<string, string> = {
  'Basic/Introductory': '기초/입문',
  'Algorithm/Data Structure': '알고리즘',
  'Past Exam Prep': '기출 대비',
  'Hash': '해시',
  'Stack/Queue': '스택/큐',
  'Heap': '힙',
  'Sort': '정렬',
  'Brute Force': '완전 탐색',
  'Greedy': '탐욕법',
  'Dynamic Programming': '동적 계획법',
  'DFS/BFS': '그래프 탐색',
  'Binary Search': '이분 탐색',
  'Graph': '그래프',
  'Kakao': '카카오 기출',
  'Contest': '대회 기출',
  'General': '일반 기출',
};

export function categoryLabel(s?: string | null): string {
  return s ? CATEGORY_LABEL[s] ?? s : '';
}

// 서버가 문구 안에 작은따옴표로 넣은 영어 분류명('DFS/BFS' 등)을 한국어 이름으로 바꾼다. 추천 이유 문구 표시용.
export function localizeAreaNames(text?: string | null): string {
  if (!text) {return '';}
  return text.replace(/'([^']+)'/g, (whole, name: string) =>
    CATEGORY_LABEL[name] ? `'${CATEGORY_LABEL[name]}'` : whole);
}

// CodeBlock이 지원하는 언어로 매핑 (Java/C++는 우선 javascript 토크나이저로 폴백)
export function toCodeLang(language?: string): 'python' | 'javascript' {
  return language?.toLowerCase().startsWith('py') ? 'python' : 'javascript';
}
