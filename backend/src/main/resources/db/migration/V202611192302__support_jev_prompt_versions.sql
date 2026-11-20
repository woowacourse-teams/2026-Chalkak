-- 프롬프트는 관리자가 직접 입력한다. 초기 내용/시드/운영 활성화 INSERT 없음.
ALTER TABLE ai_prompt_versions
    ALTER COLUMN system_prompt DROP NOT NULL,
    ALTER COLUMN user_prompt_template DROP NOT NULL,
    ALTER COLUMN parameters DROP NOT NULL,
    ADD COLUMN jev_configuration JSONB,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN activated_at TIMESTAMPTZ;

ALTER TABLE ai_prompt_versions DROP CONSTRAINT ck_ai_prompt_versions_type;
ALTER TABLE ai_prompt_versions ADD CONSTRAINT ck_ai_prompt_versions_type CHECK (
    type IN ('PHOTO_REVIEW', 'JEV_TOPIC_CLASSIFICATION', 'JEV_PHOTO_MODERATION')
);
ALTER TABLE ai_prompt_versions ADD CONSTRAINT ck_ai_prompt_versions_provider_shape CHECK (
    (type = 'PHOTO_REVIEW'
        AND system_prompt IS NOT NULL AND user_prompt_template IS NOT NULL AND parameters IS NOT NULL
        AND jev_configuration IS NULL AND active = FALSE AND activated_at IS NULL)
    OR (type IN ('JEV_TOPIC_CLASSIFICATION', 'JEV_PHOTO_MODERATION')
        AND system_prompt IS NULL AND user_prompt_template IS NULL AND parameters IS NULL
        AND jev_configuration IS NOT NULL AND jsonb_typeof(jev_configuration) = 'object'
        AND (active = FALSE OR activated_at IS NOT NULL))
);
CREATE UNIQUE INDEX ux_jev_prompt_active_role ON ai_prompt_versions (type) WHERE active;

ALTER TABLE admin_audit_logs DROP CONSTRAINT ck_admin_audit_logs_action_target;
ALTER TABLE admin_audit_logs ADD CONSTRAINT ck_admin_audit_logs_action_target CHECK (
    (target_type = 'POST' AND action IN ('POST_APPROVED', 'POST_REJECTED', 'POST_DELETED'))
    OR (target_type = 'USER' AND action IN ('USER_BANNED', 'USER_UNBANNED', 'USER_ROLE_CHANGED'))
    OR (target_type = 'TOPIC' AND action IN ('TOPIC_CREATED', 'TOPIC_UPDATED', 'TOPIC_DELETED'))
    OR (target_type = 'TOPIC_PUSH_SCHEDULE' AND action IN (
        'TOPIC_PUSH_SCHEDULE_CREATED', 'TOPIC_PUSH_SCHEDULE_UPDATED', 'TOPIC_PUSH_SCHEDULE_DELETED'))
    OR (target_type = 'NOTICE' AND action IN ('NOTICE_CREATED', 'NOTICE_UPDATED', 'NOTICE_DELETED'))
    OR (target_type = 'BANNER' AND action IN ('BANNER_CREATED', 'BANNER_DELETED'))
    OR (target_type = 'AI_PROMPT_VERSION' AND action IN (
        'AI_PROMPT_VERSION_CREATED', 'JEV_PROMPT_VERSION_CREATED', 'JEV_PROMPT_ACTIVATION_UPDATED'))
    OR (target_type = 'REMINDER' AND action IN ('REMINDER_CREATED', 'REMINDER_UPDATED', 'REMINDER_DELETED'))
    OR (target_type = 'APP_VERSION' AND action IN ('APP_VERSION_CREATED', 'APP_VERSION_UPDATED'))
    OR (target_type = 'FEEDBACK' AND action IN ('FEEDBACK_DELETED', 'FEEDBACK_ANSWERED', 'FEEDBACK_ANSWER_DELETED'))
);
