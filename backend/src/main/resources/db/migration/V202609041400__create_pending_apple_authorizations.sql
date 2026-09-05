CREATE TABLE pending_apple_authorizations (
    upload_id UUID PRIMARY KEY,
    encrypted_refresh_token VARCHAR(4096) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_pending_apple_authorizations_refresh_token_not_blank
        CHECK (btrim(encrypted_refresh_token) <> '')
);

CREATE INDEX ix_pending_apple_authorizations_expires_at
    ON pending_apple_authorizations (expires_at);
