# 하루코딩 앱 (frontend)

React Native 0.85와 TypeScript로 만든 안드로이드 앱입니다.

## 실행

```bash
npm install
npm start           # Metro
npm run android     # 다른 터미널에서 빌드·설치
npm run lint        # eslint
npm test            # jest
```

- 서버는 `../backend`에서 도커로 띄웁니다(루트 README 참고).
- 에뮬레이터는 `http://10.0.2.2:8080`으로 같은 PC의 서버에 접속합니다.

## 환경변수 (`frontend/.env`)

| 이름 | 설명 |
|---|---|
| `API_BASE_URL` | 서버 주소. 비우면 `http://10.0.2.2:8080`(에뮬레이터용). 배포용 빌드는 `https://haru.careerpilot.kr` |
| `GOOGLE_WEB_CLIENT_ID` | 구글 로그인 웹 클라이언트 ID(서버의 `GOOGLE_CLIENT_ID`와 같은 값) |

- `.env`를 바꾼 뒤에는 `npm start -- --reset-cache`로 Metro를 다시 켜야 반영됩니다.

## 배포용 APK 빌드
1. `frontend/.env`에 `API_BASE_URL=https://haru.careerpilot.kr`을 넣습니다.
2. 릴리스 서명 키로 빌드합니다.
3. 구글 로그인을 쓰려면 릴리스 서명 키의 SHA-1을 Google Cloud 콘솔의 Android OAuth 클라이언트에 등록해야 합니다.

## 화면 구성

| 하단 탭 | 화면 |
|---|---|
| 홈 | 오늘의 목표, 오늘의 문제 시작, 나의 실력 요약, 추천 학습 주제 |
| 문제 | 문제 은행(분류 필터), 오답 노트·스크랩 진입 |
| 리그 | 내 리그와 순위 |
| 학습 통계 | 전체·주간·월간·연간 탭(이번 주 활동, 월간 달력, 연간 히트맵, 유형별·난이도별 정답률, 실력 분석) |
| 마이페이지 | 프로필, 프로필 편집(언어·하루 목표·난이도), 알림, 다크 모드·글자 크기, 로그아웃·탈퇴 |

그 밖의 화면: 로그인, 회원가입, 온보딩, 온보딩 결과, 문제 풀이(AI 힌트, 코드 실행, 채점 결과), 알림, 오답 노트, 스크랩

## 폴더 구조

```
src/api/         apiFetch.ts(공통 요청 함수, 토큰 자동 갱신) + 기능별 *Api.ts
src/screens/     화면 1개 = 파일 1개
src/components/  CodeBlock(코드 표시·정렬), ProgressRing 등
src/navigation/  AppNavigator.tsx (하단 탭 + 스택)
src/theme/       ThemeContext.tsx (색 토큰, 다크 모드, 글자 크기)
src/types/       공용 타입, 표시 이름(분류·난이도·유형)
src/utils/       하루 목표 등 공용 함수
```

## 개발 규칙

- 서버 요청은 반드시 `src/api/apiFetch.ts`의 `api.get/post/patch/del`을 씁니다.
- 색은 `useTheme().colors`에서만 가져오고, 글자 크기에는 항상 `fontScale`을 곱합니다.
- 사용자에게 보이는 문구는 한국어 존댓말("~했어요")로 씁니다.
- 자세한 규칙은 `CLAUDE.md`에 있습니다.
