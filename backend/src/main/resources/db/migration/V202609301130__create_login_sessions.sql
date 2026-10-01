CREATE TABLE login_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_login_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

-- 기존 회전 계보는 첫 토큰의 생성 시각을 로그인 시각으로 이관한다.
INSERT INTO login_sessions (id, user_id, created_at)
SELECT session_id, user_id, MIN(created_at)
FROM user_refresh_tokens
GROUP BY session_id, user_id;

ALTER TABLE user_refresh_tokens
    ADD CONSTRAINT fk_user_refresh_tokens_session
        FOREIGN KEY (session_id) REFERENCES login_sessions (id) ON DELETE RESTRICT;
