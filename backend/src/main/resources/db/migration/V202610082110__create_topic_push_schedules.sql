CREATE TABLE topic_push_schedules (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    send_time TIME(0) WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_topic_push_schedules_send_time UNIQUE (send_time)
);
