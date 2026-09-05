CREATE TYPE post_image_upload_status AS ENUM (
    'ISSUED',
    'READY',
    'REJECTED'
);

CREATE TABLE post_image_uploads (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL,
    status post_image_upload_status NOT NULL DEFAULT 'ISSUED',
    rejection_reason VARCHAR(50),
    image_metadata JSONB,
    expires_at TIMESTAMPTZ NOT NULL,
    claimed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_post_image_uploads_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_post_image_uploads_user_id_created_at
    ON post_image_uploads (user_id, created_at DESC);

COMMENT ON COLUMN post_image_uploads.expires_at IS
    'Claim expiry. Independent of the shorter presigned URL lifetime.';
COMMENT ON COLUMN post_image_uploads.claimed_at IS
    'Set when a post consumes this upload. NULL means the upload is still unused.';
COMMENT ON COLUMN post_image_uploads.image_metadata IS
    'EXIF extracted by the image processor. NULL until processing completes.';
