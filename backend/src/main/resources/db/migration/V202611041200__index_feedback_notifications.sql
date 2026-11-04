CREATE INDEX idx_notifications_feedback_source
    ON notifications (source_id)
    WHERE source_type = 'FEEDBACK';
