CREATE TYPE notification_type AS ENUM (
    'POST_MODERATION_PENDING'
);

CREATE TYPE notification_channel AS ENUM (
    'SLACK'
);

CREATE TYPE notification_target AS ENUM (
    'ADMIN_MODERATION_REVIEWERS'
);

CREATE TYPE notification_outbox_status AS ENUM (
    'PENDING',
    'PROCESSING',
    'RETRY',
    'SENT',
    'FAILED'
);

CREATE TABLE notification_outboxes (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    post_id UUID NOT NULL,
    type notification_type NOT NULL,
    channel notification_channel NOT NULL,
    target notification_target NOT NULL,
    status notification_outbox_status NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    processing_token UUID,
    lease_expires_at TIMESTAMPTZ,
    last_error VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_outboxes_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE RESTRICT,
    CONSTRAINT ux_notification_outboxes_post_type_channel_target
        UNIQUE (post_id, type, channel, target),
    CONSTRAINT ck_notification_outboxes_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT ck_notification_outboxes_last_error_not_blank
        CHECK (last_error IS NULL OR btrim(last_error) <> ''),
    CONSTRAINT ck_notification_outboxes_state_complete
        CHECK (
            (
                status = 'PENDING'
                AND attempt_count = 0
                AND next_attempt_at IS NOT NULL
                AND processing_token IS NULL
                AND lease_expires_at IS NULL
                AND last_error IS NULL
            )
            OR (
                status = 'RETRY'
                AND attempt_count > 0
                AND next_attempt_at IS NOT NULL
                AND processing_token IS NULL
                AND lease_expires_at IS NULL
                AND last_error IS NOT NULL
            )
            OR (
                status = 'PROCESSING'
                AND attempt_count > 0
                AND next_attempt_at IS NULL
                AND processing_token IS NOT NULL
                AND lease_expires_at IS NOT NULL
            )
            OR (
                status = 'SENT'
                AND attempt_count > 0
                AND next_attempt_at IS NULL
                AND processing_token IS NULL
                AND lease_expires_at IS NULL
            )
            OR (
                status = 'FAILED'
                AND attempt_count > 0
                AND next_attempt_at IS NULL
                AND processing_token IS NULL
                AND lease_expires_at IS NULL
                AND last_error IS NOT NULL
            )
        )
);

-- 발송 대기와 임대 만료 복구가 각각 읽는 행만 인덱싱한다. 완료 행은 인덱스에 남기지 않는다.
CREATE INDEX ix_notification_outboxes_due_next_attempt
    ON notification_outboxes (next_attempt_at, id)
    WHERE status IN ('PENDING', 'RETRY');

CREATE INDEX ix_notification_outboxes_expired_lease
    ON notification_outboxes (lease_expires_at, id)
    WHERE status = 'PROCESSING';

COMMENT ON TABLE notification_outboxes IS
    'Transactional outbox for provider-neutral notification delivery.';
COMMENT ON COLUMN notification_outboxes.processing_token IS
    'Lease ownership token. Prevents a stale worker from overwriting a reclaimed delivery.';
