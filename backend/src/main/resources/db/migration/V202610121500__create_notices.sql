CREATE TABLE notices (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    title VARCHAR(50) NOT NULL,
    body VARCHAR(500) NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_notices_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_notices_body_not_blank CHECK (btrim(body) <> '')
);
