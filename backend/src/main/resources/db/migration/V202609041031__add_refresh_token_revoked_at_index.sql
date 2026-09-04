-- 정리 작업은 폐기된 지 오래된 토큰을 revoked_at 조건 하나로 훑는데, 여태 이 컬럼에는 인덱스가
-- 없어 매번 전체 스캔이었다. 다른 인덱스들은 살아 있는 토큰을 찾으려고 revoked_at IS NULL만 담지만,
-- 이 조건에 걸리는 행은 반대로 전부 폐기된 행이다. 그래서 부분 조건도 반대로 두어, 인덱스가
-- 계속 늘어나는 살아 있는 행을 싣지 않게 한다.
CREATE INDEX ix_user_refresh_tokens_revoked_at
    ON user_refresh_tokens (revoked_at) WHERE revoked_at IS NOT NULL;
CREATE INDEX ix_admin_refresh_tokens_revoked_at
    ON admin_refresh_tokens (revoked_at) WHERE revoked_at IS NOT NULL;
