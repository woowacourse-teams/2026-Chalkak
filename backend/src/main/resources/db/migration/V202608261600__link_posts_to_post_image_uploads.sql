ALTER TABLE posts ADD COLUMN post_image_upload_id UUID;

-- 기존 게시물은 스토리지 키에서 업로드 ID를 되찾아 연결한다. 키 규칙이 바뀌기 전에 만들어졌거나
-- 대응하는 업로드 행이 없는 게시물은 연결하지 않고 NULL로 남긴다.
UPDATE posts
SET post_image_upload_id = extracted.upload_id
FROM (
    SELECT posts.id AS post_id,
           -- UUID 모양을 그대로 강제한다. 느슨하게 열어 두면 하이픈 36개 같은 문자열도 통과해
           -- ::uuid 캐스트에서 터지고, 그러면 이 마이그레이션 전체가 실패해 배포가 멈춘다.
           -- 이미지 처리 Lambda의 staging 키 정규식과 같은 기준이다.
           substring(
               photos.original_storage_key
               from '([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\.webp$'
           )::uuid AS upload_id
    FROM posts
    JOIN photos ON photos.id = posts.photo_id
) AS extracted
WHERE posts.id = extracted.post_id
  AND extracted.upload_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM post_image_uploads WHERE post_image_uploads.id = extracted.upload_id
  );

ALTER TABLE posts
    ADD CONSTRAINT fk_posts_post_image_upload
        FOREIGN KEY (post_image_upload_id) REFERENCES post_image_uploads (id)
        ON DELETE RESTRICT;

CREATE UNIQUE INDEX ux_posts_post_image_upload_id
    ON posts (post_image_upload_id)
    WHERE post_image_upload_id IS NOT NULL;

COMMENT ON COLUMN posts.post_image_upload_id IS
    'Upload consumed by this post. Lets processing callbacks find the post without deriving a storage key.';
