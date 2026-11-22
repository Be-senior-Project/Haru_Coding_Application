-- pgvector 확장 활성화
CREATE EXTENSION IF NOT EXISTS vector;

-- ────────────────────────────────────────────
-- 1. users
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
                                     id                 BIGSERIAL PRIMARY KEY,
                                     google_id          VARCHAR(255) UNIQUE,
    email              TEXT        NOT NULL,           -- AES-GCM 암호화 저장
    email_hash         VARCHAR(255) NOT NULL UNIQUE,   -- HMAC-SHA256 (검색용)
    password           VARCHAR(255),                  -- 소셜 로그인이면 NULL
    nickname           VARCHAR(100) NOT NULL,
    profile_image_url  TEXT,
    level              INT         NOT NULL DEFAULT 1,
    xp                 INT         NOT NULL DEFAULT 0,
    streak_days        INT         NOT NULL DEFAULT 0,
    preferred_language VARCHAR(20) CHECK (preferred_language IN ('JAVA','PYTHON','C','JS')),

    -- 온보딩 역량 정보 (추천 알고리즘용)
    coding_level       VARCHAR(10)  NOT NULL DEFAULT 'NONE'
    CHECK (coding_level IN ('NONE', 'SOME', 'LOTS')),
    cote_prepared      BOOLEAN      NOT NULL DEFAULT FALSE,
    recommended_difficulty VARCHAR(10),

    -- 유저 조절 설정 (MY-018/MY-019)
    daily_goal_count   INT          NOT NULL DEFAULT 3
    CHECK (daily_goal_count BETWEEN 1 AND 10),
    difficulty_level   VARCHAR(2)
    CHECK (difficulty_level IN ('L1','L2','L3','L4','L5')), -- NULL = 자동 추천 유지

    fcm_token          TEXT,
    created_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP   NOT NULL DEFAULT NOW()
    );

-- 이미 초기화된 개발 DB에도 반영되도록 가드 병행 (CREATE TABLE IF NOT EXISTS라 컬럼 추가는 안 됨)
ALTER TABLE users ADD COLUMN IF NOT EXISTS daily_goal_count INT NOT NULL DEFAULT 3;
ALTER TABLE users ADD COLUMN IF NOT EXISTS difficulty_level VARCHAR(2);
DO $$ BEGIN
    ALTER TABLE users ADD CONSTRAINT users_daily_goal_count_check CHECK (daily_goal_count BETWEEN 1 AND 10);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
    ALTER TABLE users ADD CONSTRAINT users_difficulty_level_check CHECK (difficulty_level IN ('L1','L2','L3','L4','L5'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- ────────────────────────────────────────────
-- 2. refresh_tokens
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS refresh_tokens (
                                              id         BIGSERIAL PRIMARY KEY,
                                              user_id    BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      TEXT      NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
    );

-- ────────────────────────────────────────────
-- 3. topics (카테고리 정규화)
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS topics (
                                      id          BIGSERIAL PRIMARY KEY,
                                      name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    icon        VARCHAR(100)
    );

-- topics 초기 데이터는 서버가 시작될 때 문제 분류 3개로 만든다(TopicInitializer). 예전 한국어 주제 4개는 문제 분류와 이름이 달라 쓰이지 않았다.

-- ────────────────────────────────────────────
-- 4. problems
-- ────────────────────────────────────────────




CREATE TABLE IF NOT EXISTS problems (
                                        id                  BIGSERIAL PRIMARY KEY,
                                        set_id              VARCHAR(50),                 -- seed의 "Set Id" (예: SET-BI-01)
    type                VARCHAR(30)     NOT NULL
        CHECK (type IN ('IMPLEMENTATION','DEBUGGING','FILL_IN_THE_BLANK')),
    category            VARCHAR(100)    NOT NULL,                     -- seed의 Category
    subcategory         VARCHAR(100),                                -- seed의 Subcategory (nullable)
    difficulty          INT             NOT NULL CHECK (difficulty IN (0,1,2)),
    language            VARCHAR(20)     NOT NULL
        CHECK (language IN ('Python','Java','C++')),
    title               VARCHAR(255)    NOT NULL,
    description         TEXT            NOT NULL,
    constraints         JSONB,                                       -- 제약 조건 목록 (문자열 배열)
    io_example          JSONB,                                       -- {"input": "...", "output": "..."}
    code_skeleton       TEXT,                                        -- 코드 스켈레톤
    answer              JSONB           NOT NULL,                    -- 문자열 또는 문자열 배열
    explanation         TEXT            NOT NULL,
    concept_explanation TEXT,                                        -- seed의 set 단위 개념 설명
    hidden_tests        JSONB,                                       -- 숨겨진 테스트 [{"input","output"}] (채점 전용, 응답 미포함)
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW()
    );

-- 이미 초기화된 DB에도 반영되도록 가드 (CREATE TABLE IF NOT EXISTS라 컬럼 추가는 안 됨)
ALTER TABLE problems ADD COLUMN IF NOT EXISTS hidden_tests JSONB;

CREATE INDEX IF NOT EXISTS idx_problems_difficulty
    ON problems(difficulty);
CREATE INDEX IF NOT EXISTS idx_problems_category
    ON problems(category);
CREATE INDEX IF NOT EXISTS idx_problems_set
    ON problems(set_id);

-- ────────────────────────────────────────────
-- 4-1. problem_embeddings (추천용 임베딩 - problems와 분리)
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS problem_embeddings (
    problem_id BIGINT       PRIMARY KEY REFERENCES problems(id) ON DELETE CASCADE,
    embedding  VECTOR(1536) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_problem_embeddings_vec
    ON problem_embeddings USING hnsw (embedding vector_cosine_ops);

-- ────────────────────────────────────────────
-- 5. problem_sets (하루 1세트)
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS problem_sets (
                                            id              BIGSERIAL PRIMARY KEY,
                                            title           VARCHAR(255)     NOT NULL,
    target_date     DATE             NOT NULL UNIQUE,  -- 날짜당 1세트
    difficulty      VARCHAR(10)     NOT NULL DEFAULT '초급',
    is_ai_generated BOOLEAN          NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP        NOT NULL DEFAULT NOW()
    );

-- ────────────────────────────────────────────
-- 6. problem_set_items (세트-문제 N:M 중간 테이블)
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS problem_set_items (
                                                 id             BIGSERIAL PRIMARY KEY,
                                                 problem_set_id BIGINT NOT NULL REFERENCES problem_sets(id) ON DELETE CASCADE,
    problem_id     BIGINT NOT NULL REFERENCES problems(id),
    sort_order     INT    NOT NULL DEFAULT 0,
    UNIQUE (problem_set_id, problem_id)
    );

-- ────────────────────────────────────────────
-- 7. user_problem_records
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_problem_records (
                                                    id             BIGSERIAL PRIMARY KEY,
                                                    user_id        BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    problem_id     BIGINT    NOT NULL REFERENCES problems(id),
    is_correct     BOOLEAN   NOT NULL,
    user_answer    JSONB,               -- 유형별 답안 구조 통일
    time_spent_sec INT,
    xp_earned      INT       NOT NULL DEFAULT 0,
    solved_at      TIMESTAMP NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_upr_user    ON user_problem_records(user_id);
CREATE INDEX IF NOT EXISTS idx_upr_problem ON user_problem_records(problem_id);

-- ────────────────────────────────────────────
-- 8. user_streak_logs (날짜별 스트릭 이력)
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_streak_logs (
                                                id           BIGSERIAL PRIMARY KEY,
                                                user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    streak_date  DATE   NOT NULL,
    streak_count INT    NOT NULL DEFAULT 1,
    UNIQUE (user_id, streak_date)
    );

CREATE INDEX IF NOT EXISTS idx_streak_user ON user_streak_logs(user_id);

-- ────────────────────────────────────────────
-- 9. user_category_stats (카테고리별 성취도 캐시)
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_category_stats (
                                                   id            BIGSERIAL PRIMARY KEY,
                                                   user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic_id      BIGINT NOT NULL REFERENCES topics(id),
    total_solved  INT    NOT NULL DEFAULT 0,
    correct_count INT    NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, topic_id)
    );

-- ────────────────────────────────────────────
-- 10. user_xp_logs
-- ────────────────────────────────────────────


CREATE TABLE IF NOT EXISTS user_xp_logs (
                                            id         BIGSERIAL PRIMARY KEY,
                                            user_id    BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    xp_amount  INT       NOT NULL,
    reason     VARCHAR(30)     NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_xp_user ON user_xp_logs(user_id);

-- ────────────────────────────────────────────
-- 11. user_leagues (2차 목표 - 스키마만)
-- ────────────────────────────────────────────


CREATE TABLE IF NOT EXISTS user_leagues (
                                            id         BIGSERIAL PRIMARY KEY,
                                            user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tier       VARCHAR(20)     NOT NULL DEFAULT 'BRONZE',
    score      INT         NOT NULL DEFAULT 0,
    season     INT         NOT NULL,
    updated_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, season)              -- 시즌당 1레코드
    );

-- ────────────────────────────────────────────
-- 12. problem_scraps (스크랩한 문제 — 문제은행 탭 > 스크랩)
-- ────────────────────────────────────────────
-- 오답노트는 별도 테이블이 없다. user_problem_records의 is_correct=false 기록을 그대로 쓴다.
CREATE TABLE IF NOT EXISTS problem_scraps (
                                              id         BIGSERIAL PRIMARY KEY,
                                              user_id    BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    problem_id BIGINT    NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_problem_scraps_user_problem UNIQUE (user_id, problem_id)  -- 같은 문제 중복 스크랩 방지
    );

CREATE INDEX IF NOT EXISTS idx_scraps_user ON problem_scraps(user_id);

-- ────────────────────────────────────────────
-- 13. notifications (알림 목록·확인 — HOME-005)
-- ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notifications (
                                              id         BIGSERIAL PRIMARY KEY,
                                              user_id    BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title      VARCHAR(100) NOT NULL,
    body       TEXT      NOT NULL,
    is_read    BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id);

-- ────────────────────────────────────────────
-- 14. seed_sets (문제 생성 프롬프트에 넣는 예시 세트. 사용자에게는 서빙하지 않음)
-- ────────────────────────────────────────────
-- 예전에는 Hibernate(ddl-auto)가 만들던 테이블이다. 새 DB(운영 RDS 등)에서도 init.sql만으로
-- 전체 구조가 만들어지도록 여기에 둔다. 서버 첫 기동 시 SeedLoader가 all_seeds.json을 적재한다.
CREATE TABLE IF NOT EXISTS seed_sets (
    id                  BIGSERIAL PRIMARY KEY,
    category            VARCHAR(255) NOT NULL,
    subcategory         VARCHAR(255),
    difficulty          INT          NOT NULL,
    language            VARCHAR(255) NOT NULL,
    concept_explanation TEXT,
    problems            JSONB,
    embedding           VECTOR(1536)            -- SeedLoader가 적재 직후 채움
);

CREATE INDEX IF NOT EXISTS idx_seed_sets_filter
    ON seed_sets(category, language, difficulty);
CREATE INDEX IF NOT EXISTS idx_seed_sets_vec
    ON seed_sets USING hnsw (embedding vector_cosine_ops);