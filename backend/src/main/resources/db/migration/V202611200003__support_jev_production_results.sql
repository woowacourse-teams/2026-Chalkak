ALTER TABLE ai_prompt_versions ADD COLUMN disabled_at TIMESTAMPTZ,
    ADD COLUMN disabled_revision BIGINT NOT NULL DEFAULT 0 CHECK (disabled_revision >= 0);

ALTER TABLE jev_moderation_runs
    ALTER COLUMN classification_id DROP NOT NULL,
    ADD COLUMN disposition VARCHAR(30),
    ADD COLUMN application_reason VARCHAR(120),
    ADD COLUMN applied_at TIMESTAMPTZ,
    ADD COLUMN control_revision BIGINT NOT NULL DEFAULT 0 CHECK (control_revision >= 0),
    ADD CONSTRAINT ck_jev_disposition CHECK (
        disposition IN ('AUTO_APPROVED', 'ADMIN_REVIEW', 'SKIPPED')
    );

ALTER TABLE jev_moderation_runs DROP CONSTRAINT ck_jev_run_prompt_source;
ALTER TABLE jev_moderation_runs ADD CONSTRAINT ck_jev_run_prompt_source CHECK (
    (preview AND created_by_admin_id IS NOT NULL AND preview_draft IS NOT NULL
        AND classification_id IS NOT NULL)
    OR (NOT preview AND preview_draft IS NULL AND (
        (classification_prompt_id IS NOT NULL AND photo_prompt_id IS NOT NULL
            AND classification_id IS NOT NULL)
        OR (finished_at IS NOT NULL AND evaluation IS NOT NULL)
    ))
);

CREATE INDEX idx_jev_run_unapplied ON jev_moderation_runs(finished_at)
    WHERE NOT preview AND applied_at IS NULL AND finished_at IS NOT NULL;
