CREATE TABLE push_deliveries (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    event_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    device_results JSONB NOT NULL DEFAULT '{}'::jsonb,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_push_deliveries_event_user UNIQUE (event_id, user_id),
    CONSTRAINT ck_push_deliveries_status CHECK (status IN ('PENDING', 'COMPLETED')),
    CONSTRAINT ck_push_deliveries_completed CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    ),
    CONSTRAINT ck_push_deliveries_device_results CHECK (jsonb_typeof(device_results) = 'object')
);
