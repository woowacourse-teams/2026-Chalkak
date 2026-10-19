CREATE TABLE banners (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    title VARCHAR(50) NOT NULL,
    subtitle VARCHAR(100) NOT NULL,
    web_view_url VARCHAR(2000) NOT NULL,
    thumbnail_url VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_banners_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_banners_subtitle_not_blank CHECK (btrim(subtitle) <> ''),
    CONSTRAINT ck_banners_web_view_url_not_blank CHECK (btrim(web_view_url) <> ''),
    CONSTRAINT ck_banners_thumbnail_url_not_blank CHECK (btrim(thumbnail_url) <> '')
);

-- 긴 다국어 URL도 B-tree 항목 크기를 넘지 않도록 전체 URL의 SHA-256으로 중복을 막는다.
-- URI 검증은 원문 역슬래시를 거절하므로 bytea 변환의 이스케이프 해석이 발생하지 않는다.
CREATE UNIQUE INDEX ux_banners_web_view_url_digest ON banners (sha256(web_view_url::bytea));

ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_logs_action_target;
ALTER TABLE admin_audit_logs ADD CONSTRAINT ck_admin_audit_logs_action_target
    CHECK (
        (target_type = 'POST' AND action IN ('POST_APPROVED', 'POST_REJECTED', 'POST_DELETED'))
        OR (target_type = 'USER' AND action IN ('USER_BANNED', 'USER_UNBANNED'))
        OR (target_type = 'TOPIC' AND action IN ('TOPIC_CREATED', 'TOPIC_UPDATED', 'TOPIC_DELETED'))
        OR (target_type = 'TOPIC_PUSH_SCHEDULE' AND action IN (
            'TOPIC_PUSH_SCHEDULE_CREATED', 'TOPIC_PUSH_SCHEDULE_UPDATED', 'TOPIC_PUSH_SCHEDULE_DELETED'))
        OR (target_type = 'NOTICE' AND action IN ('NOTICE_CREATED', 'NOTICE_UPDATED', 'NOTICE_DELETED'))
        OR (target_type = 'BANNER' AND action = 'BANNER_CREATED')
    );
