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

    fcm_token          TEXT,
    created_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP   NOT NULL DEFAULT NOW()
    );

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

INSERT INTO topics (name, description, icon) VALUES
                                                 ('알고리즘',    '정렬, 탐색, 그리디, DP 등',    'account-tree'),
                                                 ('자료구조',    '스택, 큐, 트리, 그래프 등',     'data-object'),
                                                 ('언어/문법',   '언어별 문법 및 개념',            'code'),
                                                 ('모의테스트',  '실전형 종합 문제',               'assignment')
    ON CONFLICT (name) DO NOTHING;

-- ────────────────────────────────────────────
-- 4. problems
-- ────────────────────────────────────────────




CREATE TABLE IF NOT EXISTS problems (
                                        id               BIGSERIAL PRIMARY KEY,
                                        topic_id         BIGINT          NOT NULL REFERENCES topics(id),
    type             VARCHAR(30)     NOT NULL,
    difficulty       VARCHAR(10)     NOT NULL,
    style            VARCHAR(30)     NOT NULL,
    language         VARCHAR(20)     NOT NULL DEFAULT 'COMMON'
    CHECK (language IN ('JAVA','PYTHON','C','JS','COMMON')),
    title            VARCHAR(255)    NOT NULL,
    question         TEXT            NOT NULL,
    code             TEXT,
    options          JSONB,           -- 객관식 보기
    blanks           JSONB,           -- 빈칸 라벨 목록
    match_left       JSONB,           -- 매칭 왼쪽
    match_right      JSONB,           -- 매칭 오른쪽
    answer           JSONB           NOT NULL,
    explanation      TEXT            NOT NULL,
    generation_model VARCHAR(100),    -- 생성에 쓴 LLM 모델명
    embedding_model  VARCHAR(100),    -- 임베딩 모델명
    embedding        VECTOR(1536),    -- pgvector
    created_at       TIMESTAMP       NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_problems_embedding
    ON problems USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_problems_topic
    ON problems(topic_id);
CREATE INDEX IF NOT EXISTS idx_problems_difficulty
    ON problems(difficulty);

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