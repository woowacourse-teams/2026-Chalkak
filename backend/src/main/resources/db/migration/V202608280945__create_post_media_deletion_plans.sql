CREATE TYPE post_media_deletion_status AS ENUM (
    'PENDING',
    'FAILED',
    'SUCCEEDED'
);

CREATE TABLE post_media_deletion_plans (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    post_id UUID NOT NULL,
    post_image_upload_id UUID,
    staging_storage_key VARCHAR(1024),
    original_storage_key VARCHAR(1024) NOT NULL,
    thumbnail_storage_key VARCHAR(1024),
    status post_media_deletion_status NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error_code VARCHAR(100),
    next_attempt_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_post_media_deletion_plans_post UNIQUE (post_id),
    CONSTRAINT fk_post_media_deletion_plans_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_post_media_deletion_plans_upload
        FOREIGN KEY (post_image_upload_id)
        REFERENCES post_image_uploads (id) ON DELETE RESTRICT,
    CONSTRAINT ck_post_media_deletion_plans_staging_key_not_blank
        CHECK (staging_storage_key IS NULL OR btrim(staging_storage_key) <> ''),
    CONSTRAINT ck_post_media_deletion_plans_original_key_not_blank
        CHECK (btrim(original_storage_key) <> ''),
    CONSTRAINT ck_post_media_deletion_plans_thumbnail_key_not_blank
        CHECK (thumbnail_storage_key IS NULL OR btrim(thumbnail_storage_key) <> ''),
    CONSTRAINT ck_post_media_deletion_plans_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT ck_post_media_deletion_plans_last_error_not_blank
        CHECK (last_error_code IS NULL OR btrim(last_error_code) <> ''),
    CONSTRAINT ck_post_media_deletion_plans_state
        CHECK (
            (status = 'SUCCEEDED'
                AND completed_at IS NOT NULL
                AND next_attempt_at IS NULL
                AND last_error_code IS NULL)
            OR (status = 'FAILED'
                AND completed_at IS NULL
                AND next_attempt_at IS NOT NULL
                AND last_error_code IS NOT NULL)
            OR (status = 'PENDING'
                AND completed_at IS NULL
                AND next_attempt_at IS NOT NULL
                AND last_error_code IS NULL)
        )
);

CREATE INDEX ix_post_media_deletion_plans_due
    ON post_media_deletion_plans (next_attempt_at, id)
    WHERE status <> 'SUCCEEDED';

CREATE INDEX ix_post_media_deletion_plans_upload
    ON post_media_deletion_plans (post_image_upload_id)
    WHERE post_image_upload_id IS NOT NULL;

COMMENT ON TABLE post_media_deletion_plans IS
    'Durable, retryable deletion plan for post-owned S3 objects.';
COMMENT ON COLUMN post_media_deletion_plans.last_error_code IS
    'Allowlisted operational code only; never stores exception messages or object keys.';
