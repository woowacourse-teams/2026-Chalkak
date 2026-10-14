ALTER TABLE notices ADD COLUMN deleted_at TIMESTAMPTZ;

DROP INDEX ix_notices_scheduled;
CREATE INDEX ix_notices_scheduled ON notices (scheduled_at, id)
    WHERE distributed_at IS NULL AND deleted_at IS NULL;
