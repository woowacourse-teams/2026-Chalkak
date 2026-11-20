ALTER TABLE jev_moderation_runs
    ADD COLUMN unclaimed_dispatch_attempts INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_jev_run_unclaimed_dispatch_attempts
        CHECK (unclaimed_dispatch_attempts >= 0);
