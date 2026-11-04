-- 게시물별 공식 인스타그램 게시 동의 정보. 기존 게시물은 모두 미동의로 채운다.
ALTER TABLE posts
    ADD COLUMN instagram_upload_agreed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN instagram_id VARCHAR(30),
    ADD COLUMN instagram_agreed_at TIMESTAMPTZ,
    ADD COLUMN instagram_revoked_at TIMESTAMPTZ;
