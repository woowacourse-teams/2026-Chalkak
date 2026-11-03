ALTER TABLE post_like_counts
    ADD COLUMN last_milestone_count BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_post_like_counts_milestone CHECK (
        last_milestone_count >= 0 AND last_milestone_count % 5 = 0
    );

-- Establish a baseline only: do not create notifications for past milestones.
UPDATE post_like_counts
SET last_milestone_count = like_count / 5 * 5;

DROP TABLE post_like_push_milestones;

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_like_milestone_source CHECK (
        type <> 'POST_LIKE_MILESTONE'
        OR (source_type IS NOT NULL AND source_type = 'POST' AND source_id IS NOT NULL)
    );
