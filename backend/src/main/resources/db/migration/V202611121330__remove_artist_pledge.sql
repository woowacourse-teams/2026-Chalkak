ALTER TABLE users
    DROP CONSTRAINT ck_users_artist_agreed_role,
    DROP COLUMN artist_agreed_at;
