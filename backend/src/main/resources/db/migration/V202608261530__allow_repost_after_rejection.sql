DROP INDEX ux_posts_user_topic_active;

CREATE UNIQUE INDEX ux_posts_user_topic_active
    ON posts (user_id, topic_id)
    WHERE deleted_at IS NULL AND moderation_status <> 'REJECTED';

COMMENT ON INDEX ux_posts_user_topic_active IS
    'One live post per user and topic. Rejected posts are excluded so the author can upload again.';
