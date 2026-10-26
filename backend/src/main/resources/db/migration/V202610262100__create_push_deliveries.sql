CREATE TABLE push_deliveries (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    event_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    lease_token UUID,
    lease_until TIMESTAMPTZ,
    device_results JSONB NOT NULL DEFAULT '{}'::jsonb,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_push_deliveries_event_user UNIQUE (event_id, user_id),
    CONSTRAINT ck_push_deliveries_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED')),
    CONSTRAINT ck_push_deliveries_lease CHECK (
        (status = 'PROCESSING' AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'PROCESSING' AND lease_token IS NULL AND lease_until IS NULL)
    ),
    CONSTRAINT ck_push_deliveries_completed CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    ),
    CONSTRAINT ck_push_deliveries_device_results CHECK (jsonb_typeof(device_results) = 'object')
);
