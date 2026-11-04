-- 1차 생성 OFF 배포·구버전 종료 확인 후에만 실행한다.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

-- 이미 활성화했던 환경에는 도입 초기화를 적용하지 않는다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM notifications WHERE type = 'POST_LIKE_MILESTONE') THEN
        RAISE EXCEPTION 'Like notifications already exist; refusing baseline initialization';
    END IF;
END
$$;

WITH initialized AS (
    UPDATE post_like_counts
    SET last_milestone_count = like_count / 5 * 5
    WHERE last_milestone_count < like_count / 5 * 5
    RETURNING post_id
)
SELECT COUNT(*) AS initialized_rows FROM initialized;

COMMIT;
