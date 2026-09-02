ALTER TABLE notification_outboxes
    DROP CONSTRAINT fk_notification_outboxes_post;

ALTER TABLE notification_outboxes
    ADD CONSTRAINT fk_notification_outboxes_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;
