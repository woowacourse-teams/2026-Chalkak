ALTER TABLE banners ADD COLUMN deleted_at TIMESTAMPTZ;

DROP INDEX ux_banners_web_view_url_digest;
CREATE UNIQUE INDEX ux_banners_web_view_url_digest ON banners (sha256(web_view_url::bytea))
    WHERE deleted_at IS NULL;

ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_logs_action_target;
ALTER TABLE admin_audit_logs ADD CONSTRAINT ck_admin_audit_logs_action_target
    CHECK (
        (target_type = 'POST' AND action IN ('POST_APPROVED', 'POST_REJECTED', 'POST_DELETED'))
        OR (target_type = 'USER' AND action IN ('USER_BANNED', 'USER_UNBANNED'))
        OR (target_type = 'TOPIC' AND action IN ('TOPIC_CREATED', 'TOPIC_UPDATED', 'TOPIC_DELETED'))
        OR (target_type = 'TOPIC_PUSH_SCHEDULE' AND action IN (
            'TOPIC_PUSH_SCHEDULE_CREATED', 'TOPIC_PUSH_SCHEDULE_UPDATED', 'TOPIC_PUSH_SCHEDULE_DELETED'))
        OR (target_type = 'NOTICE' AND action IN ('NOTICE_CREATED', 'NOTICE_UPDATED', 'NOTICE_DELETED'))
        OR (target_type = 'BANNER' AND action IN ('BANNER_CREATED', 'BANNER_DELETED'))
    );

ALTER TABLE admin_audit_logs ADD CONSTRAINT ck_admin_audit_logs_banner_deletion_reason
    CHECK (action <> 'BANNER_DELETED' OR reason IS NOT NULL);
