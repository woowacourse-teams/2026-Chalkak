CREATE TABLE post_likes (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    post_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_post_likes_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_post_likes_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ux_post_likes_post_user UNIQUE (post_id, user_id)
);
