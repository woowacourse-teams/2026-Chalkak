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

-- 한 lineage에 행이 하나뿐이라는 부분 UNIQUE 인덱스는 의도적으로 두지 않는다.
-- 회전된 행과 폐기된 행을 재사용 탐지의 증거로 계속 남기므로, 한 lineage에는 이미 소비된 행이
-- 여러 개 쌓인다. 'lineage당 한 행'은 이 테이블이 지키는 불변식이 아니다.

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
