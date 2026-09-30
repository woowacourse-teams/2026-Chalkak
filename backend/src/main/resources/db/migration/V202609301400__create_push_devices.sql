CREATE TABLE push_devices (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    session_id UUID NOT NULL,
    fcm_token TEXT,
    fcm_token_hash VARCHAR(64),
    registered_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    disabled_at TIMESTAMPTZ,
    CONSTRAINT fk_push_devices_session
        FOREIGN KEY (session_id) REFERENCES login_sessions (id) ON DELETE RESTRICT,
    CONSTRAINT ux_push_devices_session UNIQUE (session_id),
    CONSTRAINT ck_push_devices_token_pair CHECK (
        (fcm_token IS NULL AND fcm_token_hash IS NULL)
        OR (fcm_token IS NOT NULL AND fcm_token_hash IS NOT NULL
            AND fcm_token_hash ~ '^[0-9a-f]{64}$')
    ),
    CONSTRAINT ck_push_devices_active_token
        CHECK (disabled_at IS NOT NULL OR fcm_token IS NOT NULL)
);

-- 가변 길이 토큰 원문 대신 고정 길이 해시로 활성 연결의 중복을 방지한다.
CREATE UNIQUE INDEX ux_push_devices_active_fcm_token
    ON push_devices (fcm_token_hash) WHERE disabled_at IS NULL;
