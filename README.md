# 하루코딩 추천 알고리즘 설계서 v1.0

## 개요

사용자의 역량과 풀이 이력을 바탕으로 3단계 필터링을 통해 최적의 문제를 추천하는 알고리즘입니다.
넷플릭스의 콘텐츠 기반 필터링(Content-Based Filtering) 방식을 참고하여 설계하였습니다.

---

## 추천 흐름

```
사용자 입력 (온보딩 or 풀이 이력)
        ↓
  Level 1: 난이도 결정
        ↓
  Level 2: 토픽 우선순위
        ↓
  Level 3: 언어 + 유형 세부 필터
        ↓
  RecommendationFilterDto 반환
```

---

## Level 1 - 난이도 결정

| 조건 | 결과 |
|------|------|
| 신규 유저 (totalSolved < 10) | 온보딩 점수로 결정 |
| 정답률 ≥ 80% | 난이도 UP (입문→초급→중급→고급) |
| 정답률 40~79% | 현재 난이도 유지 |
| 정답률 < 40% | 난이도 DOWN |

**온보딩 점수 계산**

```
NONE = 0점 / SOME = 30점 / LOTS = 60점 / 코테 경험 있음 = +40점

0~24점  → 입문
25~49점 → 초급
50~74점 → 중급
75점~   → 고급
```

---

## Level 2 - 토픽 우선순위

`user_category_stats` 기반 약점/미탐색/강점 분류

| 조건 | 분류 | 우선순위 |
|------|------|---------|
| 정답률 < 50% AND 풀이 ≥ 3 | 🔴 약점 토픽 | 최우선 |
| 풀이 수 = 0 | 🟡 미탐색 토픽 | 차순위 |
| 정답률 ≥ 80% | 🟢 강점 토픽 | 복습용 |

**우선순위: 약점 > 미탐색 > 강점**

---

## Level 3 - 언어 + 유형 세부 필터

| 조건 | 결과 |
|------|------|
| preferredLanguage 있음 | 해당 언어 문제 우선 |
| preferredLanguage 없음 | COMMON |
| 많이 틀린 유형 있음 | 해당 유형 집중 추천 |
| 유형 데이터 없음 | 객관식 기본 추천 |

---

## 최종 추천 결과물

```json
{
  "difficulty": "초급",
  "topicIds": [1, 3],
  "language": "JAVA",
  "type": "구현",
  "reason": "알고리즘 정답률이 낮아요. JAVA 구현 문제부터 시작해봐요!",
  "focusPoint": "알고리즘 기초 + 구현력 강화",
  "method": "ai | rule_based"
}
```

---

## API 엔드포인트

| 엔드포인트 | 설명 |
|-----------|------|
| `POST /api/recommend/onboarding` | 신규 유저 추천 |
| `POST /api/recommend/personalized` | 기존 유저 개인화 추천 |

---

## 관련 파일

| 파일 | 역할 |
|------|------|
| `RecommendationService.java` | 추천 로직 구현 |
| `RecommendationController.java` | API 엔드포인트 |
| `UserProfileDto.java` | 요청 DTO |
| `RecommendationFilterDto.java` | 응답 DTO |
| `resources/prompts/` | GPT 프롬프트 템플릿 |