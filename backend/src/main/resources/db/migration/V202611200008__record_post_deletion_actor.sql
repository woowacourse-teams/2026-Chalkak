ALTER TABLE posts
    ADD COLUMN deleted_by_author BOOLEAN,
    ADD CONSTRAINT ck_posts_deletion_actor
        CHECK (deleted_by_author IS NULL OR deleted_at IS NOT NULL);
