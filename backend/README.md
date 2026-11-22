# 하루코딩 서버 (backend)

Java 17, Spring Boot 3.5, PostgreSQL 16 + pgvector로 만든 API 서버입니다.

## 패키지 구조

```
com.besenior.harucoding
├── controller    API 엔드포인트
├── service       회원·문제·채점·통계·추천 로직
├── generation    AI 문제 생성
│   ├── GenerationPipeline        생성 → 검증 → 저장
│   ├── ProblemSetSessionService  오늘의 세트 구성
│   ├── GenerationQuota           사용자별 하루 생성 한도
│   ├── seed                      시드 문제 적재(SeedLoader)
│   └── verify                    코드 실행·채점(Python·Java·C++ 실행기)
├── entity / repository / DTO
└── global        설정, 보안(JWT·관리자 키), 암호화, 예외, 공용 규칙(DifficultyRules·LanguageRules)
```

## 핵심 흐름

### 오늘의 세트 (`POST /api/problems/start-set`)
1. **약점 몫:** 약점 분류가 있으면 세트의 절반(올림)을 그 분류의 안 푼 문제로 채웁니다. 같은 언어, 사용자 난이도 ±1 안에서 약점 하위 분류를 먼저 고릅니다.
2. **추천 몫:** 나머지는 추천 엔진(`ProblemRecommendationService`)이 고릅니다.
3. **생성:** 그래도 모자라면 약점 분류(없으면 기초/입문)로 부족한 만큼 AI가 만듭니다. 사용자별 하루 한도(`GENERATION_DAILY_LIMIT`) 안에서만 합니다.

- DB 조회는 짧은 트랜잭션 안에서 끝냅니다. OpenAI를 기다리는 동안에는 DB 연결을 잡지 않습니다.

### 약점 판단 (`WeaknessAnalyzer`)
- 문제별 첫 제출 기준으로, 3문제 이상 풀고 정답률 70% 미만인 분류 중 가장 낮은 곳을 약점으로 봅니다.
- 하위 분류는 그 분류 안에서 정답률이 가장 낮은 곳입니다.
- 통계, 추천, 오늘의 세트가 같은 기준을 씁니다.

### 문제 생성 (`GenerationPipeline`)
1. 같은 분류·난이도·언어의 시드 세트를 예시로 골라 프롬프트(`resources/prompts/generation_en.md`)에 넣습니다.
2. OpenAI가 JSON으로 문제를 만듭니다.
3. 정답 코드를 실제로 실행해 예시 출력과 맞는지 확인합니다.
   - 디버깅 문제는 버그 코드가 예시에서 실제로 틀리는지, 정답과 정확히 1줄만 다른지도 확인합니다.
4. 숨겨진 테스트의 기대 출력은 AI가 쓴 값이 아니라 정답 코드를 실행해 계산합니다.
5. 통과한 문제만 저장하고 임베딩을 만듭니다.

- 재시도는 최대 3번이고, 생성을 시작한 뒤 30초 안에서만 합니다.

### 채점 (`POST /api/problems/{id}/attempt`)
- 구현·디버깅은 코드를 실행해 예시와 숨겨진 테스트를 모두 맞혀야 정답입니다.
- 빈칸 채우기는 숨겨진 테스트가 있으면 실행해서 채점하고, 없으면 칸별로 문자열을 비교합니다.
- 오답이면 이유를 돌려줍니다. 숨겨진 테스트의 입력과 출력은 응답에 넣지 않습니다.
- XP는 문제별 첫 정답에만 줍니다.

### 학습 통계 (`GET /api/stats/me?period=ALL|WEEK|MONTH|YEAR`)
- 문제별 첫 제출 기준으로 셉니다.
- 응답에는 기간별 풀이 수, 정답률, 이번 주 활동, 분류별 통계, 약점 분류, 날짜별 풀이 수, 유형별·난이도별 정답률이 들어 있습니다.

## 보안

| 항목 | 내용 |
|---|---|
| 사용자 코드 실행 | 별도 계정, 네트워크 차단, 메모리·프로세스 수 제한. 실행 시간은 5초, 답안과 요청 크기도 제한 |
| 인증 | JWT(액세스 1시간, 리프레시 7일). 서명 키는 환경변수 `JWT_SECRET` |
| 개인정보 | 이메일은 AES-GCM 암호화, 검색은 HMAC 해시. 비밀번호는 BCrypt |
| 관리용 API | 문제 직접 생성·임베딩 일괄 생성은 로그인과 `X-Admin-Key`가 모두 필요 |
| 외부 호출 | OpenAI·구글 호출은 연결 5초, 응답 60초 제한 |

## 환경변수

`.env.example`에 항목, 필수 여부, 값 만드는 방법이 있습니다. 주요 항목은 다음과 같습니다.

| 이름 | 필수 | 설명 |
|---|---|---|
| `OPENAI_API_KEY` | 필수 | 문제 생성과 임베딩 |
| `ENCRYPTION_SECRET_KEY` | 필수 | 개인정보 암호화 키(32바이트 base64) |
| `JWT_SECRET` | 필수 | JWT 서명 키(32바이트 이상) |
| `DB_PASSWORD` | 필수 | DB 비밀번호(compose가 DB와 서버에 같이 넘김) |
| `GOOGLE_CLIENT_ID` | 구글 로그인 시 | 앱의 웹 클라이언트 ID |
| `ADMIN_API_KEY` | 선택 | 관리용 API 키 |
| `GENERATION_DAILY_LIMIT` | 선택 | 사용자별 하루 생성 한도(기본 30) |
| `API_BIND` | 선택 | API 포트를 열 주소(기본 127.0.0.1) |

## 실행과 테스트

```bash
# 실행 (DB + API)
docker compose -p harucoding up -d --build

# 테스트 (JDK 17이 없으면 도커로)
docker run --rm -v "$PWD:/work" -v haru-gradle-cache:/root/.gradle -w /work eclipse-temurin:17-jdk bash gradlew test
```

- DB 구조는 처음 만들 때 `init.sql`이 만들고, 엔티티에 새로 생긴 컬럼은 Hibernate(`ddl-auto=update`)가 채웁니다.
- 서버를 켜면 시드 문제(`resources/json/all_seeds.json`)를 적재하고(`seed.autoload`), 문제 분류 3개의 주제를 준비합니다.
- `HaruCodingBackendApplicationTests`는 `DB_URL` 환경변수가 있을 때만 실행됩니다.

## 문서

- ERD: `docs/erd.dbml`
- API·기능 정의서: 팀 노션
