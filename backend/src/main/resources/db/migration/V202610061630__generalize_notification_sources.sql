ALTER TABLE notifications
    ADD COLUMN source_type VARCHAR(32),
    ADD COLUMN source_id UUID,
    ADD COLUMN payload JSONB;

UPDATE notifications
SET source_type = 'POST',
    source_id = post_id,
    payload = CASE
        WHEN type = 'POST_REJECTED'
            THEN jsonb_build_object('rejectionReason', rejection_reason)
        ELSE NULL
    END;

ALTER TABLE notifications
    DROP CONSTRAINT fk_notifications_post,
    DROP CONSTRAINT fk_notifications_event,
    DROP CONSTRAINT ck_notifications_type,
    DROP CONSTRAINT ck_notifications_rejection_reason,
    DROP COLUMN post_id,
    DROP COLUMN rejection_reason,
    ADD CONSTRAINT ck_notifications_type_not_blank CHECK (btrim(type) <> ''),
    ADD CONSTRAINT ck_notifications_source_pair CHECK (
        (source_type IS NULL AND source_id IS NULL)
        OR (source_type IS NOT NULL AND source_id IS NOT NULL AND btrim(source_type) <> '')
    ),
    ADD CONSTRAINT ck_notifications_moderation_source CHECK (
        type NOT IN ('POST_APPROVED', 'POST_REJECTED')
        OR (source_type IS NOT NULL AND source_type = 'POST' AND source_id IS NOT NULL)
    ),
    ADD CONSTRAINT ck_notifications_payload_object CHECK (
        payload IS NULL OR jsonb_typeof(payload) = 'object'
    ),
    ADD CONSTRAINT ck_notifications_rejection_reason CHECK (
        (type <> 'POST_APPROVED' OR payload IS NULL OR NOT (payload ? 'rejectionReason'))
        AND (
            type <> 'POST_REJECTED'
            OR (
                payload IS NOT NULL
                AND payload ? 'rejectionReason'
                AND jsonb_typeof(payload -> 'rejectionReason') = 'string'
                AND btrim(payload ->> 'rejectionReason') <> ''
                AND char_length(payload ->> 'rejectionReason') <= 500
            )
        )
    );
