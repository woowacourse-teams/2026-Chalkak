-- AI 리뷰 자동 재시도에 쓰는 컬럼. 기존 행은 처음 요청한 회차 하나로 채운다.
ALTER TABLE ai_reviews
    ADD COLUMN round_attempt INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN request_count INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN next_attempt_at TIMESTAMPTZ,
    ADD COLUMN last_requested_at TIMESTAMPTZ;

UPDATE ai_reviews SET round_attempt = attempt, last_requested_at = requested_at;

ALTER TABLE ai_reviews
    ALTER COLUMN last_requested_at SET NOT NULL;

ALTER TABLE ai_reviews
    ADD CONSTRAINT ck_ai_reviews_next_attempt_queued
        CHECK (next_attempt_at IS NULL OR status = 'QUEUED'),
    ADD CONSTRAINT ck_ai_reviews_round_attempt_not_negative CHECK (round_attempt >= 0),
    ADD CONSTRAINT ck_ai_reviews_round_attempt_within_attempt CHECK (round_attempt <= attempt),
    ADD CONSTRAINT ck_ai_reviews_round_attempt_after_queued
        CHECK (status = 'QUEUED' OR round_attempt >= 1),
    ADD CONSTRAINT ck_ai_reviews_request_count_positive CHECK (request_count >= 1),
    ADD CONSTRAINT ck_ai_reviews_last_requested_at_order CHECK (last_requested_at >= requested_at);

COMMENT ON COLUMN ai_reviews.round_attempt IS
    '이번 회차(사용자 요청 1회) 안에서 워커가 가져간 횟수. 재시도와 임대 회수를 모두 센다. 자동 시도 상한은 이 값으로 비교한다.';
COMMENT ON COLUMN ai_reviews.request_count IS
    '사용자 요청 횟수(처음 요청 포함)';
COMMENT ON COLUMN ai_reviews.next_attempt_at IS
    '자동 재시도로 대기 중일 때 다시 가져갈 수 있는 시각. 이 시각 전에는 워커가 가져가지 않는다.';
COMMENT ON COLUMN ai_reviews.last_requested_at IS
    '이번 회차의 사용자 요청 시각. 처음 요청 시각은 requested_at에 남는다.';

-- 대기열 조회가 COALESCE(next_attempt_at, last_requested_at), id 순으로 정렬하므로 인덱스를 같은 식으로 바꿔 별도 정렬 없이 첫 행을 읽게 한다.
DROP INDEX ix_ai_reviews_claimable;
CREATE INDEX ix_ai_reviews_claimable
    ON ai_reviews ((COALESCE(next_attempt_at, last_requested_at)), id)
    WHERE status IN ('QUEUED', 'PROCESSING');
