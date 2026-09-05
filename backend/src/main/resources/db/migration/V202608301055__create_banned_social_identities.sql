CREATE TABLE banned_social_identities (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    provider social_provider NOT NULL,
    subject_hmac VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_banned_social_identities_provider_subject_hmac
        UNIQUE (provider, subject_hmac),
    CONSTRAINT ck_banned_social_identities_subject_hmac_format
        CHECK (subject_hmac ~ '^[0-9a-f]{64}$')
);
