-- 실제 프롬프트 본문이나 운영 활성화 시드는 넣지 않는다.
CREATE TABLE jev_topic_categories (
    id UUID PRIMARY KEY,
    topic_id UUID NOT NULL REFERENCES topics(id),
    classification_key VARCHAR(64) NOT NULL UNIQUE,
    phase JSONB NOT NULL,
    category VARCHAR(64),
    evaluation JSONB,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE jev_moderation_runs (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES posts(id),
    topic_id UUID NOT NULL REFERENCES topics(id),
    topic_title VARCHAR(255) NOT NULL,
    storage_key VARCHAR(1024) NOT NULL,
    preview BOOLEAN NOT NULL,
    created_by_admin_id UUID REFERENCES admins(id),
    classification_id UUID NOT NULL REFERENCES jev_topic_categories(id),
    classification_prompt_id UUID REFERENCES ai_prompt_versions(id),
    photo_prompt_id UUID REFERENCES ai_prompt_versions(id),
    preview_draft JSONB,
    phase JSONB NOT NULL,
    evaluation JSONB,
    finished_at TIMESTAMPTZ,
    dispatch_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_jev_run_prompt_source CHECK (
        (preview AND created_by_admin_id IS NOT NULL AND preview_draft IS NOT NULL)
        OR (NOT preview AND preview_draft IS NULL
            AND classification_prompt_id IS NOT NULL AND photo_prompt_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX ux_jev_run_production_post ON jev_moderation_runs(post_id) WHERE NOT preview;
CREATE INDEX idx_jev_run_dispatch ON jev_moderation_runs(dispatch_at) WHERE finished_at IS NULL;
CREATE INDEX idx_jev_run_post_created ON jev_moderation_runs(post_id, created_at DESC);
