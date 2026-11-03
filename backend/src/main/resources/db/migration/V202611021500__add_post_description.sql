-- 기존 게시물은 설명이 없으므로 채우지 않고 NULL(설명 없음)로 둔다.
ALTER TABLE posts
    ADD COLUMN description VARCHAR(300);
