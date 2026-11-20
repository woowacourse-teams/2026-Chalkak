ALTER TABLE jev_moderation_runs
    ADD COLUMN dispatch_attempts INTEGER NOT NULL DEFAULT 0 CHECK (dispatch_attempts >= 0),
    ADD COLUMN dispatch_error VARCHAR(120);
