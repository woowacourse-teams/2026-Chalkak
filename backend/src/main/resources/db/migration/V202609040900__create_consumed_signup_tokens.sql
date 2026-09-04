CREATE TABLE consumed_signup_tokens (
    jti VARCHAR(36) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_consumed_signup_tokens_jti_not_blank CHECK (btrim(jti) <> '')
);

-- 만료된 기록을 주기적으로 지우므로, 그 삭제가 전체 스캔이 되지 않도록 만료 시각에 인덱스를 둔다.
CREATE INDEX ix_consumed_signup_tokens_expires_at
    ON consumed_signup_tokens (expires_at);
