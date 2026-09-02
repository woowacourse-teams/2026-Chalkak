CREATE TYPE admin_action AS ENUM (
    'POST_APPROVED',
    'POST_REJECTED',
    'POST_DELETED',
    'USER_BANNED',
    'USER_UNBANNED',
    'TOPIC_CREATED',
    'TOPIC_UPDATED',
    'TOPIC_DELETED'
);

CREATE TYPE admin_target_type AS ENUM (
    'POST',
    'USER',
    'TOPIC'
);

CREATE TABLE admin_audit_logs (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    actor_admin_id UUID NOT NULL,
    action admin_action NOT NULL,
    target_type admin_target_type NOT NULL,
    target_id UUID NOT NULL,
    reason VARCHAR(500),
    before_state JSONB NOT NULL DEFAULT '{}'::jsonb,
    after_state JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL,
    request_id UUID NOT NULL,
    CONSTRAINT fk_admin_audit_logs_actor_admin
        FOREIGN KEY (actor_admin_id) REFERENCES admins (id) ON DELETE RESTRICT,
    CONSTRAINT ck_admin_audit_logs_reason_not_blank
        CHECK (reason IS NULL OR btrim(reason) <> ''),
    CONSTRAINT ck_admin_audit_logs_before_state_object
        CHECK (jsonb_typeof(before_state) = 'object'),
    CONSTRAINT ck_admin_audit_logs_after_state_object
        CHECK (jsonb_typeof(after_state) = 'object'),
    CONSTRAINT ck_admin_audit_logs_changed_state_present
        CHECK (before_state <> '{}'::jsonb OR after_state <> '{}'::jsonb),
    CONSTRAINT ck_admin_audit_logs_action_target
        CHECK (
            (target_type = 'POST' AND action IN (
                'POST_APPROVED', 'POST_REJECTED', 'POST_DELETED'
            ))
            OR (target_type = 'USER' AND action IN (
                'USER_BANNED', 'USER_UNBANNED'
            ))
            OR (target_type = 'TOPIC' AND action IN (
                'TOPIC_CREATED', 'TOPIC_UPDATED', 'TOPIC_DELETED'
            ))
        ),
    CONSTRAINT ck_admin_audit_logs_required_reason
        CHECK (
            action NOT IN (
                'POST_REJECTED',
                'POST_DELETED',
                'USER_BANNED',
                'USER_UNBANNED',
                'TOPIC_DELETED'
            )
            OR reason IS NOT NULL
        )
);

CREATE INDEX ix_admin_audit_logs_occurred_at_id
    ON admin_audit_logs (occurred_at DESC, id DESC);

CREATE INDEX ix_admin_audit_logs_actor_occurred_at_id
    ON admin_audit_logs (actor_admin_id, occurred_at DESC, id DESC);

CREATE INDEX ix_admin_audit_logs_action_occurred_at_id
    ON admin_audit_logs (action, occurred_at DESC, id DESC);

CREATE INDEX ix_admin_audit_logs_target_occurred_at_id
    ON admin_audit_logs (target_type, target_id, occurred_at DESC, id DESC);

CREATE INDEX ix_admin_audit_logs_request_id
    ON admin_audit_logs (request_id);

COMMENT ON TABLE admin_audit_logs IS
    'Append-only admin history. Application code must not update or delete rows.';
COMMENT ON COLUMN admin_audit_logs.before_state IS
    'Allowlisted non-sensitive business state before the administrator action.';
COMMENT ON COLUMN admin_audit_logs.after_state IS
    'Allowlisted non-sensitive business state after the administrator action.';
COMMENT ON COLUMN admin_audit_logs.request_id IS
    'Request trace identifier. Multiple audit rows may share the same value.';
