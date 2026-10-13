ALTER TABLE notices
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    ADD COLUMN distributed_at TIMESTAMPTZ;

ALTER TABLE notices
    ALTER COLUMN status DROP DEFAULT,
    ADD CONSTRAINT ck_notices_status CHECK (status IN ('SCHEDULED', 'DISTRIBUTED')),
    ADD CONSTRAINT ck_notices_distributed_at CHECK (
        (status = 'SCHEDULED' AND distributed_at IS NULL)
        OR (status = 'DISTRIBUTED' AND distributed_at IS NOT NULL)
    );

CREATE INDEX ix_notices_scheduled ON notices (scheduled_at, id) WHERE status = 'SCHEDULED';

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_notice_source CHECK (
        type <> 'NOTICE'
        OR (source_type = 'NOTICE' AND source_id IS NOT NULL)
    );
