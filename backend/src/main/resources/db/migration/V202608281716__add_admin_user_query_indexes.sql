CREATE INDEX ix_users_admin_created_at
    ON users (created_at DESC, id DESC);

CREATE INDEX ix_users_admin_status_created_at
    ON users (status, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_users_admin_withdrawn_created_at
    ON users (created_at DESC, id DESC)
    WHERE deleted_at IS NOT NULL;

CREATE INDEX ix_posts_admin_user_moderation_status
    ON posts (user_id, moderation_status);
