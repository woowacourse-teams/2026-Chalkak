CREATE TYPE social_provider AS ENUM (
    'GOOGLE',
    'KAKAO',
    'APPLE'
);

CREATE TABLE social_accounts (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL,
    provider social_provider NOT NULL,
    subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_social_accounts_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ux_social_accounts_provider_subject UNIQUE (provider, subject),
    CONSTRAINT ux_social_accounts_user_id UNIQUE (user_id),
    CONSTRAINT ck_social_accounts_subject_not_blank CHECK (btrim(subject) <> '')
);

ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
DROP INDEX ux_users_email_lower_active;
