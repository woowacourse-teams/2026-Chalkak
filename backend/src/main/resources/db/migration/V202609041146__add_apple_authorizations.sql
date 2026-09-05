CREATE TABLE apple_authorizations (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    social_account_id UUID NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    encrypted_refresh_token VARCHAR(4096) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_apple_authorizations_social_account
        FOREIGN KEY (social_account_id) REFERENCES social_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ux_apple_authorizations_social_account_client
        UNIQUE (social_account_id, client_id),
    CONSTRAINT ck_apple_authorizations_client_id_not_blank
        CHECK (btrim(client_id) <> ''),
    CONSTRAINT ck_apple_authorizations_refresh_token_not_blank
        CHECK (btrim(encrypted_refresh_token) <> '')
);
