-- 프롬프트 버전은 만든 뒤 바꾸지 않는다. 유형별 최신 버전이 사용자 기능에 쓰인다.
CREATE TABLE ai_prompt_versions (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    type VARCHAR(30) NOT NULL,
    version INTEGER NOT NULL,
    system_prompt TEXT NOT NULL,
    user_prompt_template TEXT NOT NULL,
    model VARCHAR(100) NOT NULL,
    parameters JSONB NOT NULL,
    output_schema_version VARCHAR(50) NOT NULL,
    memo VARCHAR(500),
    created_by_admin_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_ai_prompt_versions_created_by_admin
        FOREIGN KEY (created_by_admin_id) REFERENCES admins (id) ON DELETE RESTRICT,
    CONSTRAINT ux_ai_prompt_versions_type_version UNIQUE (type, version),
    CONSTRAINT ck_ai_prompt_versions_type CHECK (type IN ('PHOTO_REVIEW')),
    CONSTRAINT ck_ai_prompt_versions_version_positive CHECK (version >= 1),
    CONSTRAINT ck_ai_prompt_versions_system_prompt_not_blank CHECK (btrim(system_prompt) <> ''),
    CONSTRAINT ck_ai_prompt_versions_user_prompt_template_not_blank
        CHECK (btrim(user_prompt_template) <> ''),
    CONSTRAINT ck_ai_prompt_versions_model_not_blank CHECK (btrim(model) <> ''),
    CONSTRAINT ck_ai_prompt_versions_parameters_object CHECK (jsonb_typeof(parameters) = 'object'),
    CONSTRAINT ck_ai_prompt_versions_output_schema_version_not_blank
        CHECK (btrim(output_schema_version) <> ''),
    CONSTRAINT ck_ai_prompt_versions_memo_not_blank CHECK (memo IS NULL OR btrim(memo) <> '')
);

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
        OR (target_type = 'AI_PROMPT_VERSION' AND action = 'AI_PROMPT_VERSION_CREATED')
    );
