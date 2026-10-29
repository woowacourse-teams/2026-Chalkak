ALTER TABLE app_versions
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    ADD COLUMN last_changed_by UUID REFERENCES admins(id),
    ADD COLUMN change_reason VARCHAR(500),
    ADD CONSTRAINT ck_app_versions_change_metadata CHECK (
        (last_changed_by IS NULL AND change_reason IS NULL)
        OR (last_changed_by IS NOT NULL AND change_reason IS NOT NULL AND btrim(change_reason) <> '')
    );

ALTER TABLE app_versions DROP CONSTRAINT ck_app_versions_minimum_supported_version;
ALTER TABLE app_versions ADD CONSTRAINT ck_app_versions_minimum_supported_version CHECK (
    CASE WHEN minimum_supported_version ~ '^(0|[1-9][0-9]{0,9})\.(0|[1-9][0-9]{0,9})\.(0|[1-9][0-9]{0,9})$'
    THEN split_part(minimum_supported_version, '.', 1)::BIGINT <= 2147483647
        AND split_part(minimum_supported_version, '.', 2)::BIGINT <= 2147483647
        AND split_part(minimum_supported_version, '.', 3)::BIGINT <= 2147483647
    ELSE FALSE END
);
ALTER TABLE app_versions ADD CONSTRAINT ck_app_versions_store_url CHECK (
    char_length(store_url) <= 2000
    AND store_url !~ '^[[:space:]]|[[:space:]]$' AND (
        (platform = 'ANDROID' AND store_url ~* '^https://play\.google\.com(:443)?/store/apps/details\?')
        OR (platform = 'IOS' AND store_url ~* '^https://apps\.apple\.com(:443)?/')
    )
);

ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_logs_action_target;
ALTER TABLE admin_audit_logs ADD CONSTRAINT ck_admin_audit_logs_action_target CHECK (
    (target_type = 'POST' AND action IN ('POST_APPROVED', 'POST_REJECTED', 'POST_DELETED'))
    OR (target_type = 'USER' AND action IN ('USER_BANNED', 'USER_UNBANNED'))
    OR (target_type = 'TOPIC' AND action IN ('TOPIC_CREATED', 'TOPIC_UPDATED', 'TOPIC_DELETED'))
    OR (target_type = 'TOPIC_PUSH_SCHEDULE' AND action IN (
        'TOPIC_PUSH_SCHEDULE_CREATED', 'TOPIC_PUSH_SCHEDULE_UPDATED', 'TOPIC_PUSH_SCHEDULE_DELETED'))
    OR (target_type = 'NOTICE' AND action IN ('NOTICE_CREATED', 'NOTICE_UPDATED', 'NOTICE_DELETED'))
    OR (target_type = 'BANNER' AND action IN ('BANNER_CREATED', 'BANNER_DELETED'))
    OR (target_type = 'AI_PROMPT_VERSION' AND action = 'AI_PROMPT_VERSION_CREATED')
    OR (target_type = 'REMINDER' AND action IN ('REMINDER_CREATED', 'REMINDER_UPDATED', 'REMINDER_DELETED'))
    OR (target_type = 'APP_VERSION' AND action IN ('APP_VERSION_CREATED', 'APP_VERSION_UPDATED'))
);
ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_logs_required_reason;
ALTER TABLE admin_audit_logs ADD CONSTRAINT ck_admin_audit_logs_required_reason CHECK (
    action NOT IN (
        'POST_REJECTED', 'POST_DELETED', 'USER_BANNED', 'USER_UNBANNED', 'TOPIC_DELETED',
        'APP_VERSION_CREATED', 'APP_VERSION_UPDATED'
    ) OR reason IS NOT NULL
);
