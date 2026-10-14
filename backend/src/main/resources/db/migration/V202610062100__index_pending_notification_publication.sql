CREATE INDEX ix_notifications_pending_publication
    ON notifications (next_attempt_at, id) WHERE sqs_publish_status = 'PENDING';
