// 하루 목표 문제 수. 오늘의 문제 세트 크기와 홈 "오늘의 목표"가 같은 값을 쓴다.
// 서버 기본값(3)이나 예전에 저장된 더 작은 값이 와도 최소 4문제로 맞춘다.
export const DAILY_GOAL_MIN = 4;
export const DAILY_GOAL_MAX = 10;

export function effectiveDailyGoal(saved: number | null | undefined): number {
  return Math.min(DAILY_GOAL_MAX, Math.max(DAILY_GOAL_MIN, saved ?? DAILY_GOAL_MIN));
}
