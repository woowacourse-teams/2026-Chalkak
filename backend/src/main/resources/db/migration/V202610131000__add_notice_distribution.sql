ALTER TABLE notices ADD COLUMN distributed_at TIMESTAMPTZ;

CREATE INDEX ix_notices_scheduled ON notices (scheduled_at, id) WHERE distributed_at IS NULL;

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_notice_source CHECK (
        type <> 'NOTICE'
        OR (source_type = 'NOTICE' AND source_id IS NOT NULL)
    );
