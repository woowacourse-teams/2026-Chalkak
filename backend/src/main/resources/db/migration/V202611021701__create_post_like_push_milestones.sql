CREATE TABLE post_like_push_milestones (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    post_id UUID NOT NULL,
    user_id UUID NOT NULL,
    milestone_count BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    sqs_publish_status VARCHAR(20) NOT NULL,
    next_attempt_at TIMESTAMPTZ,
    sqs_published_at TIMESTAMPTZ,
    CONSTRAINT fk_post_like_push_milestones_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_post_like_push_milestones_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ux_post_like_push_milestones_post_count
        UNIQUE (post_id, milestone_count),
    CONSTRAINT ck_post_like_push_milestones_count CHECK (
        milestone_count >= 5 AND milestone_count % 5 = 0
    ),
    CONSTRAINT ck_post_like_push_milestones_status CHECK (
        sqs_publish_status IN ('PENDING', 'PUBLISHED', 'EXPIRED', 'FAILED')
    ),
    CONSTRAINT ck_post_like_push_milestones_timestamps CHECK (
        (sqs_publish_status = 'PENDING'
            AND next_attempt_at IS NOT NULL AND sqs_published_at IS NULL)
        OR (sqs_publish_status = 'PUBLISHED'
            AND next_attempt_at IS NULL AND sqs_published_at IS NOT NULL)
        OR (sqs_publish_status IN ('EXPIRED', 'FAILED')
            AND next_attempt_at IS NULL AND sqs_published_at IS NULL)
    )
);

CREATE INDEX ix_post_like_push_milestones_pending
    ON post_like_push_milestones (next_attempt_at, id)
    WHERE sqs_publish_status = 'PENDING';
