CREATE TABLE user_refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL,
    session_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    rotated_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ux_user_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_user_refresh_tokens_token_hash_format
        CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_user_refresh_tokens_expiry_order
        CHECK (expires_at <= absolute_expires_at)
);

CREATE INDEX ix_user_refresh_tokens_session_id
    ON user_refresh_tokens (session_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_user_refresh_tokens_user_id
    ON user_refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_user_refresh_tokens_absolute_expires_at
    ON user_refresh_tokens (absolute_expires_at);

-- 한 lineage에 살아 있는 토큰이 하나뿐이라는 부분 UNIQUE 인덱스는 의도적으로 두지 않는다.
-- 재사용 유예 구간 동안 회전된 토큰과 새 토큰이 잠시 함께 살아 있어야 하므로, 그런 인덱스는
-- 정상적인 동시 재발급을 제약 위반으로 만들어 버린다.

COMMENT ON COLUMN user_refresh_tokens.token_hash IS
    'SHA-256 hex of the opaque refresh token. The token value itself is never stored.';
COMMENT ON COLUMN user_refresh_tokens.session_id IS
    'Rotation lineage of one device. Reuse of a rotated token revokes only this lineage.';
COMMENT ON COLUMN user_refresh_tokens.expires_at IS
    'Inactivity expiry. Renewed on every rotation, but never past absolute_expires_at.';
COMMENT ON COLUMN user_refresh_tokens.absolute_expires_at IS
    'Absolute expiry fixed at initial login. Rotation never extends it, forcing re-login.';
COMMENT ON COLUMN user_refresh_tokens.rotated_at IS
    'Set when this token was exchanged for a successor. A later reuse means token theft.';
COMMENT ON COLUMN user_refresh_tokens.revoked_at IS
    'Set when the token was invalidated. NULL means the token is still live.';
