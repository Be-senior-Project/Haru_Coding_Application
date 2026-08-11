// 새 백엔드 problems 스키마 기준 목 데이터 (실 API 연동 전 데모용)
import type {Problem} from '../types/problem';

export type {Problem} from '../types/problem';
export type {ProblemType, IOExample} from '../types/problem';

// 데모용 세트(묶음) — 백엔드의 set_id 문자열과는 별개로 화면 시연을 위한 그룹
export interface ProblemSet {
  id: string;
  title: string;
  targetDate: string; // 'YYYY-MM-DD'
  difficulty: number; // 0 | 1 | 2
  problems: Problem[];
}

// ─── 문제 데이터 ────────────────────────────────────────────
const allProblems: Problem[] = [
  // ── 세트 1 ─────────────────────────────────────────────
  {
    id: 1,
    setId: 'SET-BI-01',
    type: 'IMPLEMENTATION',
    category: 'Basic/Introductory',
    subcategory: null,
    difficulty: 0,
    language: 'Python',
    title: '짝수의 개수 세기',
    description: '정수 배열 arr에서 짝수의 개수를 구해 반환하세요.',
    constraints: [
      '1 <= len(arr) <= 100',
      'arr의 원소는 -1000 이상 1000 이하의 정수이다',
    ],
    ioExample: {input: 'arr = [1, 2, 3, 4, 6]', output: '3'},
    codeSkeleton: 'def solution(arr):\n    cnt = 0\n    {{CORE}}\n    return cnt',
    answer: '    for x in arr:\n        if x % 2 == 0:\n            cnt += 1',
    explanation:
      '배열의 각 원소를 순회하면서 2로 나누어 떨어지는지 확인하면 된다. 짝수일 때마다 카운트를 1씩 증가시키면 정답을 구할 수 있다. 시간 복잡도는 O(n).',
    conceptExplanation:
      '반복문과 조건문으로 배열을 훑으며 단순한 값을 계산하는 기본 구현 연습.',
  },
  {
    id: 2,
    setId: 'SET-BI-01',
    type: 'DEBUGGING',
    category: 'Basic/Introductory',
    subcategory: null,
    difficulty: 0,
    language: 'Python',
    title: '배열의 합 구하기',
    description:
      '정수 배열 arr의 모든 원소의 합을 반환하세요. 아래 코드에는 오류가 있습니다.',
    constraints: [
      '1 <= len(arr) <= 100',
      'arr의 원소는 -1000 이상 1000 이하의 정수이다',
    ],
    ioExample: {input: 'arr = [2, 4, 6]', output: '12'},
    codeSkeleton:
      'def solution(arr):\n    s = 0\n    for i in range(len(arr) - 1):\n        s += arr[i]\n    return s',
    answer:
      'def solution(arr):\n    s = 0\n    for i in range(len(arr)):\n        s += arr[i]\n    return s',
    explanation:
      '마지막 원소를 누락한 반복 범위가 오류의 원인이다. range(len(arr))로 배열 전체를 순회해야 한다.',
    conceptExplanation:
      '반복 범위 off-by-one 오류를 찾아 고치는 디버깅 연습.',
  },
  {
    id: 3,
    setId: 'SET-BI-01',
    type: 'FILL_IN_THE_BLANK',
    category: 'Basic/Introductory',
    subcategory: null,
    difficulty: 0,
    language: 'Python',
    title: '최댓값 찾기',
    description:
      '정수 배열 arr에서 가장 큰 값을 반환하세요. 배열은 비어 있지 않습니다.',
    constraints: [
      '1 <= len(arr) <= 100',
      'arr의 원소는 -1000 이상 1000 이하의 정수이다',
    ],
    ioExample: {input: 'arr = [3, 7, 2, 9, 5]', output: '9'},
    codeSkeleton:
      'def solution(arr):\n    mx = arr[0]\n    for x in arr:\n        if {{BLANK_1}}:\n            {{BLANK_2}}\n    return mx',
    answer: ['x > mx', 'mx = x'],
    explanation:
      '초기 최댓값을 첫 원소로 두고, 배열을 순회하며 더 큰 값이 나오면 갱신한다. 시간 복잡도는 O(n).',
    conceptExplanation: '순회하며 조건에 따라 값을 갱신하는 빈칸 채우기 연습.',
  },

  // ── 세트 2 ─────────────────────────────────────────────
  {
    id: 4,
    setId: 'SET-ADS-01',
    type: 'IMPLEMENTATION',
    category: 'Algorithm/Data Structure',
    subcategory: 'DFS/BFS',
    difficulty: 1,
    language: 'Python',
    title: '그래프의 연결 요소 개수',
    description:
      '노드 n개와 간선 목록 edges가 주어질 때, 연결 요소(connected component)의 개수를 반환하세요.',
    constraints: [
      '1 <= n <= 1000',
      '0 <= len(edges) <= 2000',
    ],
    ioExample: {input: 'n = 5\nedges = [[0,1],[1,2],[3,4]]', output: '2'},
    codeSkeleton:
      'def solution(n, edges):\n    graph = [[] for _ in range(n)]\n    for a, b in edges:\n        graph[a].append(b)\n        graph[b].append(a)\n    visited = [False] * n\n    count = 0\n    {{CORE}}\n    return count',
    answer:
      '    def dfs(node):\n        visited[node] = True\n        for nxt in graph[node]:\n            if not visited[nxt]:\n                dfs(nxt)\n    for i in range(n):\n        if not visited[i]:\n            dfs(i)\n            count += 1',
    explanation:
      '방문하지 않은 노드에서 DFS를 시작할 때마다 연결 요소가 하나씩 늘어난다. 모든 노드를 한 번씩 방문하므로 O(n + e).',
    conceptExplanation:
      'DFS/BFS로 그래프를 순회하며 연결 요소를 세는 대표 유형.',
  },
  {
    id: 5,
    setId: 'SET-ADS-01',
    type: 'FILL_IN_THE_BLANK',
    category: 'Algorithm/Data Structure',
    subcategory: 'Dynamic Programming',
    difficulty: 1,
    language: 'Python',
    title: '피보나치 수 (DP)',
    description: 'n번째 피보나치 수를 반환하세요. (F(0)=0, F(1)=1)',
    constraints: ['0 <= n <= 30'],
    ioExample: {input: 'n = 7', output: '13'},
    codeSkeleton:
      'def solution(n):\n    if n < 2:\n        return n\n    dp = [0] * (n + 1)\n    dp[1] = 1\n    for i in range(2, n + 1):\n        dp[i] = {{BLANK_1}}\n    return {{BLANK_2}}',
    answer: ['dp[i - 1] + dp[i - 2]', 'dp[n]'],
    explanation:
      '이전 두 항의 합으로 현재 항을 채우는 전형적인 1차원 DP. 시간 복잡도 O(n).',
    conceptExplanation: '점화식을 그대로 배열에 채우는 바텀업 DP 연습.',
  },
];

// ─── 세트 정의 ────────────────────────────────────────────
export const problemSets: ProblemSet[] = [
  {
    id: 'set-1',
    title: '오늘의 코딩 도전',
    targetDate: '2026-05-24',
    difficulty: 0,
    problems: allProblems.filter(p => p.setId === 'SET-BI-01'),
  },
  {
    id: 'set-2',
    title: '오늘의 코딩 도전',
    targetDate: '2026-05-25',
    difficulty: 1,
    problems: allProblems.filter(p => p.setId === 'SET-ADS-01'),
  },
];

// ─── 유틸 함수 ────────────────────────────────────────────
export const getTodaySet = (): ProblemSet | null => {
  const today = new Date().toISOString().split('T')[0];
  return problemSets.find(s => s.targetDate === today) ?? problemSets[0];
};

export const getProblemById = (id: number): Problem | undefined =>
  allProblems.find(p => p.id === id);
