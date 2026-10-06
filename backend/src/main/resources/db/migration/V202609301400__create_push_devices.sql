CREATE TABLE push_devices (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL,
    session_id UUID NOT NULL,
    fcm_token TEXT NOT NULL,
    fcm_token_hash VARCHAR(64) NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_push_devices_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ux_push_devices_session UNIQUE (session_id),
    CONSTRAINT ux_push_devices_fcm_token UNIQUE (fcm_token_hash),
    CONSTRAINT ck_push_devices_fcm_token_hash CHECK (
        fcm_token_hash ~ '^[0-9a-f]{64}$'
    )
);
