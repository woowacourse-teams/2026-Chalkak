CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL,
    post_id UUID NOT NULL,
    event_key UUID NOT NULL,
    type VARCHAR(32) NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    rejection_reason VARCHAR(500),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_notifications_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_notifications_event
        FOREIGN KEY (event_key) REFERENCES admin_audit_logs (id) ON DELETE RESTRICT,
    CONSTRAINT ux_notifications_user_event UNIQUE (user_id, event_key),
    CONSTRAINT ck_notifications_type
        CHECK (type IN ('POST_APPROVED', 'POST_REJECTED')),
    CONSTRAINT ck_notifications_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_notifications_body_not_blank CHECK (btrim(body) <> ''),
    CONSTRAINT ck_notifications_rejection_reason
        CHECK (
            (type = 'POST_APPROVED' AND rejection_reason IS NULL)
            OR (
                type = 'POST_REJECTED'
                AND rejection_reason IS NOT NULL
                AND btrim(rejection_reason) <> ''
            )
        )
);

CREATE INDEX ix_notifications_user_created_at_id
    ON notifications (user_id, created_at DESC, id DESC);

CREATE INDEX ix_notifications_user_unread
    ON notifications (user_id)
    WHERE read_at IS NULL;
