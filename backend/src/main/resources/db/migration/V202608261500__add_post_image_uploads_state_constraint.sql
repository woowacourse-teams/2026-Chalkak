ALTER TABLE post_image_uploads
    ADD CONSTRAINT ck_post_image_uploads_state_complete
        CHECK (
            (
                status = 'ISSUED'
                AND rejection_reason IS NULL
                AND image_metadata IS NULL
            )
            OR
            (
                status = 'READY'
                AND rejection_reason IS NULL
                AND image_metadata IS NOT NULL
            )
            OR
            (
                status = 'REJECTED'
                AND rejection_reason IS NOT NULL
            )
        );

CREATE INDEX ix_post_image_uploads_status_expires_at
    ON post_image_uploads (status, expires_at)
    WHERE claimed_at IS NULL;

COMMENT ON CONSTRAINT ck_post_image_uploads_state_complete ON post_image_uploads IS
    'Keeps status consistent with the columns it implies, so a rejected row always carries a reason.';
