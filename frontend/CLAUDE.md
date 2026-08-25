# Haru_Coding_front

React Native 0.85 / TypeScript / Android 에뮬레이터 기준. 백엔드는 `../backend` (팀원 담당, 수정 금지 — 문제는 프론트 쪽에서 해결).

## 실행

```bash
npm start           # Metro
npm run android     # 빌드 + 설치 (별도 터미널)
npm run lint        # eslint
npm test            # jest
```

백엔드는 도커로 띄운다 (`../backend`에서 실행. Docker Desktop엔 `harucoding` 프로젝트로 뜨고, 이후엔 ▶ 버튼으로 기동/중지):

```bash
docker compose -p harucoding up -d --build   # 폴더명이 backend라 -p로 프로젝트명 고정
```

- API `localhost:8080`, DB `localhost:5433`.
- `backend/.env` 필수. `ENCRYPTION_SECRET_KEY`(32바이트 base64)가 비면 `AesGcmEncryptor` 생성이 실패해 **서버가 기동조차 안 된다**.
- `OPENAI_API_KEY`가 비면 `SeedLoader`가 임베딩 401로 죽어 `seed_sets`·`problems`가 0건이 된다 → 문제 은행·오늘의 문제가 빈 상태. topics 시드 4개만 들어감.

## 구조

```
src/api/         apiFetch.ts(공통 래퍼) + 도메인별 *Api.ts
src/screens/     화면 1개 = 파일 1개
src/navigation/  AppNavigator.tsx (bottom-tabs + native-stack)
src/theme/       ThemeContext.tsx
src/types/       공용 타입, env.d.ts
src/data/        mock 데이터 + 로컬 저장(scrap.ts)
```

## 규칙

- 네트워크 호출은 반드시 `src/api/apiFetch.ts`의 `api.get/post/patch/del` 사용. `fetch` 직접 호출 금지.
- `BASE_URL = http://10.0.2.2:8080` (안드로이드 에뮬레이터 → 호스트 localhost). 실기기 테스트 시에만 변경.
- 토큰: `accessToken`/`refreshToken`을 AsyncStorage에 저장. 401/403 자동 갱신은 apiFetch가 처리하므로 화면에서 재시도 로직 추가 금지.
- 응답은 `json.data ?? json`으로 이미 언랩됨. 화면에서 `.data` 다시 벗기지 말 것.
- 사용자에게 보이는 문구·에러 메시지는 한국어 존댓말 ("~했어요" 톤).
- 비밀값은 `.env` + `react-native-dotenv`. 하드코딩 금지.
- 커밋에 `Co-Authored-By: Claude` 트레일러 넣지 않음.

## 디자인

- 색은 `useTheme().colors`에서만 가져온다. 화면 파일에 hex 하드코딩 금지 — 다크모드가 깨진다. 새 색이 필요하면 `ThemeContext.tsx`의 `Colors`에 토큰으로 추가.
- 의미가 고정된 토큰: `success`(정답), `danger`(오답/삭제), `warning`(주의/팁), `streak`(연속학습), `info`(중립 강조). 각각 `*Soft`는 같은 의미의 연한 배경. 다른 용도로 재사용 금지.
- primary 배경 위 글자는 `onPrimary`. 티어는 `tierFromLevel(level)`로 라벨·색을 한 번에 받는다 (색만 필요하면 `tierColor()`).
- 예외적으로 hex 고정이 맞는 곳: `CodeBlock`의 문법 강조 색(항상 어두운 에디터), Google 브랜드 색. 그 외엔 토큰.
- AI 기본값 티가 나는 것들 — 쓰지 말 것:
  - Inter·Roboto·시스템 기본 글꼴에 의존 (코드 표시는 `monospace` 예외)
  - 보라→파랑 그라데이션 배경
  - 카드 안에 카드 중첩 (여백과 `border`로 구분)
  - 색 있는 배경 위 회색 본문 (`subText`는 `bg`/`card` 위에서만)
  - 튕기는 바운스 애니메이션
- 글자 크기는 항상 `fontScale`을 곱한다 (`fontSize: 14 * fs`). 접근성 설정이 동작해야 함.

## 응답 방식 (토큰 절약)

- 서두("좋은 질문이에요")·맺음말("도움이 되었길") 생략. 요청 반복 설명 금지.
- 수정 전 해당 파일 먼저 읽기. 전체 재작성 대신 부분 수정.
- 요청한 범위만. 요청 없는 리팩터링·추상화·테스트 추가 금지.
- 코드 변경 후 `npm run lint` 통과 확인하고 마무리.
- 이 파일의 규칙보다 사용자의 직접 지시가 우선.

## 기록 (프론트 관련 지시·결정)

<!-- 앞으로도 반복 적용될 규칙·결정만 한 줄씩 추가. 일회성 작업 내역은 넣지 않음. -->

- (미완) 로그인 "이메일 기억하기" 체크박스 변경 + 회원가입 창 UI 변경. 어떻게 바꿀지는 미정 — 착수 전 사용자에게 확인할 것.
- 오답 노트·스크랩 진입은 문제 은행 화면 상단 버튼 2개. 마이페이지에는 넣지 않음.
- 화면은 `ScrapScreen`/`WrongNoteScreen`(팀원 작성)을 그대로 쓰고, 데이터는 `scrapApi`(`/api/scraps`)·`wrongNoteApi`(`/api/wrong-notes`) 서버 연동. 로컬 저장으로 다시 만들지 말 것.
- 문제 은행 목록 카드에는 문제 앞 아이콘을 두지 않는다.
- 문제 은행은 `GET /api/problems` 실데이터를 쓴다. 요청 실패거나 0건이면 목 데이터로 폴백하되 이유를 구분해 안내(`mockReason`: 연결 실패 / 등록된 문제 없음). 폴백 중일 땐 카드 탭이 `setId`(목 세트)로, 실데이터일 땐 `problemId`로 간다.
- 백엔드에 없는 것 (프론트에서 우회 중): `DELETE /api/users/me`(탈퇴 → 준비 중 안내), 유저 `tier` 필드(→ level에서 `tierFromLevel`로 파생).
- 아직 안 붙인 백엔드 API: `/api/problem-sets/*`(오늘의 세트 — 홈은 `/api/problems/start-set` 사용 중), `/api/problems/generate`, `/v1/nesting/format`.
- 공개(비로그인) API: `GET /api/topics`, `GET /api/problems`, `GET /api/problems/{id}`, `/api/recommend/**`. 나머지는 인증 필요 → `api.get(path, false)` 여부를 SecurityConfig 기준으로 맞출 것.