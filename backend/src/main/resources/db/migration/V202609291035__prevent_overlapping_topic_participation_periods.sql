ALTER TABLE topics
    ADD CONSTRAINT ex_topics_active_participation_period
    EXCLUDE USING gist (
        tstzrange(starts_at, ends_at, '[)') WITH &&
    )
    WHERE (deleted_at IS NULL);
