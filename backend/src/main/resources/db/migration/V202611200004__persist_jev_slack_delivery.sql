ALTER TABLE jev_moderation_runs
    ADD COLUMN slack_delivery JSONB,
    ADD COLUMN slack_due_at TIMESTAMPTZ;

CREATE INDEX idx_jev_slack_due ON jev_moderation_runs(slack_due_at)
    WHERE slack_due_at IS NOT NULL;
