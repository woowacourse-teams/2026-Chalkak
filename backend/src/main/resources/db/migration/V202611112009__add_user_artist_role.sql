ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER',
    ADD COLUMN artist_agreed_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ARTIST')),
    ADD CONSTRAINT ck_users_artist_agreed_role CHECK (artist_agreed_at IS NULL OR role = 'ARTIST');
