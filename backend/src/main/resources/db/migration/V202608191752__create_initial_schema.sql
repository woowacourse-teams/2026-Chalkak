CREATE TYPE user_status AS ENUM (
    'ACTIVE',
    'BANNED'
);

CREATE TYPE moderation_status AS ENUM (
    'VALIDATING',
    'PENDING',
    'APPROVED',
    'REJECTED'
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(320) NOT NULL,
    status user_status NOT NULL DEFAULT 'ACTIVE',
    signature_original_storage_key VARCHAR(1024) NOT NULL,
    signature_thumbnail_storage_key VARCHAR(1024),
    app_version VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_users_email_not_blank CHECK (btrim(email) <> ''),
    CONSTRAINT ck_users_signature_original_storage_key_not_blank
        CHECK (btrim(signature_original_storage_key) <> '')
);

CREATE UNIQUE INDEX ux_users_email_lower
    ON users (lower(email));

CREATE UNIQUE INDEX ux_users_signature_original_storage_key
    ON users (signature_original_storage_key);

CREATE TABLE topics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    topic_date DATE NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_topics_topic_date UNIQUE (topic_date),
    CONSTRAINT ck_topics_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_topics_time_range CHECK (end_at > start_at)
);

CREATE TABLE photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_storage_key VARCHAR(1024) NOT NULL,
    thumbnail_storage_key VARCHAR(1024),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    deletion_requested_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_photos_original_storage_key UNIQUE (original_storage_key),
    CONSTRAINT ck_photos_original_storage_key_not_blank
        CHECK (btrim(original_storage_key) <> '')
);

CREATE TABLE posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    topic_id UUID NOT NULL,
    photo_id UUID NOT NULL,
    title VARCHAR(10),
    moderation_status moderation_status NOT NULL DEFAULT 'VALIDATING',
    moderated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_posts_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_posts_topic
        FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE RESTRICT,
    CONSTRAINT fk_posts_photo
        FOREIGN KEY (photo_id) REFERENCES photos (id) ON DELETE RESTRICT,
    CONSTRAINT ux_posts_user_topic UNIQUE (user_id, topic_id),
    CONSTRAINT ux_posts_photo_id UNIQUE (photo_id)
);

CREATE INDEX ix_posts_feed
    ON posts (topic_id, created_at DESC, id DESC);

CREATE TABLE admins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_admins_username UNIQUE (username),
    CONSTRAINT ck_admins_username_not_blank CHECK (btrim(username) <> ''),
    CONSTRAINT ck_admins_password_not_blank CHECK (btrim(password) <> '')
);

COMMENT ON COLUMN admins.password IS
    'Stores only a one-way password hash, never a plaintext password.';
