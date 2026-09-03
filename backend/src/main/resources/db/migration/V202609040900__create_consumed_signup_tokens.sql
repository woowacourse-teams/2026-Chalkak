CREATE TABLE consumed_signup_tokens (
    jti VARCHAR(36) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_consumed_signup_tokens_jti_not_blank CHECK (btrim(jti) <> '')
);
