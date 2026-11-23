# 하루코딩 (Haru Coding)

매일 조금씩 코딩 테스트를 준비하는 안드로이드 앱입니다.
AI가 사용자의 약점에 맞춰 문제를 만들고, 서버가 코드를 실제로 실행해 채점합니다.

## 주요 기능

| 기능 | 내용 |
|---|---|
| 오늘의 문제 | 하루 목표(4~10문제)만큼 세트를 시작합니다. 절반은 약점 분류의 안 푼 문제, 나머지는 추천 문제이고, 모자라면 AI가 바로 만듭니다. |
| 문제 유형 | 구현(핵심 몇 줄 작성), 디버깅(틀린 부분 고치기), 빈칸 채우기. 언어는 Python·Java·C++ |
| 채점 | 예시 입출력과 숨겨진 테스트로 채점합니다. 오답이면 이유(컴파일 오류, 실행 오류, 시간 초과, 출력 차이)를 보여 줍니다. |
| AI 힌트 | 문제마다 3단계 힌트(방향 → 구체적 → 코드 구조) |
| 학습 통계 | 기간별(전체·주간·월간·연간) 풀이 수와 정답률, 월간 달력, 연간 히트맵, 유형별·난이도별 정답률, 약점 분류 |
| 그 밖에 | 온보딩 추천, 리그, 스크랩, 오답 노트, 알림, 다크 모드·글자 크기 |

난이도는 입문·초급·중급 3단계이고, 프로그래머스 Lv.0~2에 해당합니다.

## 아키텍처

```mermaid
flowchart LR
    App["안드로이드 앱<br/>(React Native)"] -->|HTTPS| CF["Cloudflare<br/>DNS·프록시"]
    CF --> Caddy["Caddy<br/>TLS 인증서"]
    Caddy --> API["Spring Boot API<br/>(Docker)"]
    API --> DB[("PostgreSQL 16<br/>+ pgvector")]
    API --> Runner["코드 실행기<br/>Python·Java·C++<br/>(격리 실행)"]
    API -->|문제 생성·임베딩| OpenAI["OpenAI API"]
    App -->|로그인| Google["Google 로그인"]
    API -->|토큰 확인| Google
```

- 서버는 Docker Compose로 API와 DB를 함께 띄웁니다. 외부 요청은 Caddy를 거쳐서만 들어옵니다.
- 사용자 코드는 API 컨테이너 안에서 별도 계정, 네트워크 차단, 자원 제한 상태로 실행합니다.
- 문제 추천은 문제 임베딩(pgvector) 유사도와 풀이 기록으로 고릅니다.

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| 앱 | React Native 0.85, TypeScript, React Navigation 7 |
| 서버 | Java 17, Spring Boot 3.5, Spring Security(JWT), Spring Data JPA |
| DB | PostgreSQL 16, pgvector |
| AI | OpenAI API(문제 생성, 임베딩 text-embedding-3-small) |
| 배포 | Docker Compose, Caddy, Cloudflare |

## 폴더 구조

```
backend/    Spring Boot 서버 (자세한 내용: backend/README.md)
frontend/   React Native 앱 (자세한 내용: frontend/README.md)
```

## 로컬에서 실행하기

### 1. 서버

```bash
cd backend
cp .env.example .env        # 값 채우기 (항목 설명은 .env.example 주석 참고)
docker compose -p harucoding up -d --build
```

- API는 `http://localhost:8080`, DB는 `localhost:5433`입니다.
- 처음 켤 때 시드 문제 247개와 임베딩을 적재합니다. `OPENAI_API_KEY`가 없거나 틀리면 문제 은행이 비어 있게 됩니다.
- `JWT_SECRET`, `ENCRYPTION_SECRET_KEY`, `DB_PASSWORD`가 비어 있으면 서버가 시작되지 않습니다.

### 2. 앱 (안드로이드 에뮬레이터)

```bash
cd frontend
npm install
npm start          # Metro
npm run android    # 다른 터미널에서 빌드·설치
```

에뮬레이터는 `http://10.0.2.2:8080`으로 같은 PC의 서버에 접속합니다.

## 배포

| 항목 | 내용 |
|---|---|
| 서버 주소 | `https://haru.careerpilot.kr` |
| 구성 | Cloudflare(DNS·프록시) → Caddy(TLS) → Docker Compose(Spring Boot + PostgreSQL) |
| 환경변수 | 서버의 `backend/.env`에 넣습니다. 항목은 `backend/.env.example`을 참고합니다. |
| 다시 배포 | 서버에서 최신 코드를 받은 뒤 `docker compose -p harucoding up -d --build` |
| 앱 빌드 | `frontend/.env`에 `API_BASE_URL=https://haru.careerpilot.kr`을 넣고 빌드합니다. |

- Cloudflare는 100초 안에 응답이 없으면 오류(524)를 돌려줍니다. 서버는 외부 호출 시간 제한과 문제 생성 재시도 제한으로 이 시간 안에 응답합니다.
- DB와 API 포트는 서버 안(127.0.0.1)에서만 열립니다.

## 문서

- ERD: `backend/docs/erd.dbml` (dbdiagram.io에 붙여 넣어 볼 수 있습니다)
- 기능 정의서, API 정의서: 팀 노션
