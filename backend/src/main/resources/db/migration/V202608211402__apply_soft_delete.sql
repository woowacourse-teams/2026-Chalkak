ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE topics ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE photos ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE posts ADD COLUMN deleted_at TIMESTAMPTZ;

COMMENT ON COLUMN users.deleted_at IS
    'Soft delete marker. NULL means the row is alive; otherwise it holds the deletion time.';
COMMENT ON COLUMN topics.deleted_at IS
    'Soft delete marker. NULL means the row is alive; otherwise it holds the deletion time.';
COMMENT ON COLUMN photos.deleted_at IS
    'Soft delete marker. NULL means the row is alive; otherwise it holds the deletion time.';
COMMENT ON COLUMN posts.deleted_at IS
    'Soft delete marker. NULL means the row is alive; otherwise it holds the deletion time.';

ALTER TABLE photos DROP COLUMN deletion_requested_at;

DROP INDEX ux_users_email_lower;
CREATE UNIQUE INDEX ux_users_email_lower_active
    ON users (lower(email))
    WHERE deleted_at IS NULL;

ALTER TABLE topics DROP CONSTRAINT ux_topics_topic_date;
CREATE UNIQUE INDEX ux_topics_topic_date_active
    ON topics (topic_date)
    WHERE deleted_at IS NULL;

ALTER TABLE posts DROP CONSTRAINT ux_posts_user_topic;
CREATE UNIQUE INDEX ux_posts_user_topic_active
    ON posts (user_id, topic_id)
    WHERE deleted_at IS NULL;

DROP INDEX ix_posts_feed;
CREATE INDEX ix_posts_feed
    ON posts (topic_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;
