CREATE INDEX ix_notifications_feedback_source
    ON notifications (source_id)
    WHERE source_type = 'FEEDBACK';

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_feedback_answer_source CHECK (
        type <> 'FEEDBACK_ANSWERED'
        OR (source_type IS NOT DISTINCT FROM 'FEEDBACK' AND source_id IS NOT NULL)
    );
