CREATE INDEX ix_posts_admin_user_moderation_status
    ON posts (user_id, moderation_status);
