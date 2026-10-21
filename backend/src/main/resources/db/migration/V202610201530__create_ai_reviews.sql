-- 게시물당 AI 리뷰 한 건. 행은 요청 시점에 만들어지고 이후 상태만 바뀐다.
CREATE TABLE ai_reviews (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    post_id UUID NOT NULL,
    user_id UUID NOT NULL,
    -- 요청 시점의 최신 버전을 고정한다. 처리 중 새 버전이 생겨도 바뀌지 않는다.
    prompt_version_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    -- 워커가 가져갈 때마다 늘고 줄지 않는다. 늦게 끝난 워커의 반영을 막는 기준이다.
    attempt INTEGER NOT NULL DEFAULT 0,
    lease_expires_at TIMESTAMPTZ,
    -- API 계약 형태의 결과와 모델 원출력 원본
    result JSONB,
    output JSONB,
    input_tokens INTEGER,
    cached_input_tokens INTEGER,
    output_tokens INTEGER,
    reasoning_tokens INTEGER,
    latency_ms BIGINT,
    openai_response_id VARCHAR(100),
    error_code VARCHAR(50),
    request_id UUID NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    CONSTRAINT fk_ai_reviews_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_reviews_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_reviews_prompt_version
        FOREIGN KEY (prompt_version_id) REFERENCES ai_prompt_versions (id) ON DELETE RESTRICT,
    CONSTRAINT ux_ai_reviews_post_id UNIQUE (post_id),
    CONSTRAINT ck_ai_reviews_status
        CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_ai_reviews_attempt_not_negative CHECK (attempt >= 0),
    -- 대기로 되돌릴 수 있도록 QUEUED의 started_at·error_code는 막지 않는다.
    CONSTRAINT ck_ai_reviews_attempt_after_queued CHECK (status = 'QUEUED' OR attempt >= 1),
    CONSTRAINT ck_ai_reviews_lease_processing
        CHECK ((lease_expires_at IS NOT NULL) = (status = 'PROCESSING')),
    CONSTRAINT ck_ai_reviews_result_completed CHECK ((result IS NOT NULL) = (status = 'COMPLETED')),
    CONSTRAINT ck_ai_reviews_output_completed CHECK ((output IS NOT NULL) = (status = 'COMPLETED')),
    CONSTRAINT ck_ai_reviews_completed_at_terminal
        CHECK ((completed_at IS NOT NULL) = (status IN ('COMPLETED', 'FAILED'))),
    CONSTRAINT ck_ai_reviews_failed_error_code CHECK (status <> 'FAILED' OR error_code IS NOT NULL),
    CONSTRAINT ck_ai_reviews_result_object
        CHECK (result IS NULL OR jsonb_typeof(result) = 'object'),
    CONSTRAINT ck_ai_reviews_output_object
        CHECK (output IS NULL OR jsonb_typeof(output) = 'object')
);

-- 워커가 가져갈 후보(대기, 임대가 끝났을 수 있는 처리 중)만 오래된 요청 순으로 찾는다.
CREATE INDEX ix_ai_reviews_claimable ON ai_reviews (requested_at, id)
    WHERE status IN ('QUEUED', 'PROCESSING');
