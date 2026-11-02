CREATE TABLE post_like_counts (
    post_id UUID PRIMARY KEY,
    like_count BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_post_like_counts_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_post_like_counts_non_negative CHECK (like_count >= 0)
);

-- Stop all previous writers before backfilling, including soft-deleted and zero-like posts.
INSERT INTO post_like_counts (post_id, like_count)
SELECT post.id, COUNT(post_like.id)
FROM posts post
LEFT JOIN post_likes post_like ON post_like.post_id = post.id
GROUP BY post.id;
