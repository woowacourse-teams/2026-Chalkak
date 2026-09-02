CREATE TYPE signature_processing_status AS ENUM (
    'PROCESSING',
    'FAILED'
);

ALTER TABLE users
    ADD COLUMN pending_signature_upload_id UUID,
    ADD COLUMN signature_processing_status signature_processing_status,
    ADD COLUMN signature_processing_started_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_users_signature_processing_state_complete
        CHECK (
            (
                pending_signature_upload_id IS NULL
                AND signature_processing_status IS NULL
                AND signature_processing_started_at IS NULL
            )
            OR
            (
                pending_signature_upload_id IS NOT NULL
                AND signature_processing_status IS NOT NULL
                AND signature_processing_started_at IS NOT NULL
            )
        );

CREATE UNIQUE INDEX ux_users_pending_signature_upload_id
    ON users (pending_signature_upload_id)
    WHERE pending_signature_upload_id IS NOT NULL;
