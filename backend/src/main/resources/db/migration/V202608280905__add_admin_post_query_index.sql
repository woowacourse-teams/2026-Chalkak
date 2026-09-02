CREATE INDEX ix_posts_admin_created_at_id
    ON posts (created_at DESC, id DESC);

COMMENT ON INDEX ix_posts_admin_created_at_id IS
    'Supports admin post history pagination, including soft-deleted rows.';
