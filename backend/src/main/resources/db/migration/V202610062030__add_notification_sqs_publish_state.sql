-- 기존 알림은 소급 발송하지 않는다. 새 검수 알림은 생성 당시 수신 설정으로 초기 상태를 정한다.
ALTER TABLE notifications
    ADD COLUMN sqs_publish_status VARCHAR(20),
    ADD COLUMN next_attempt_at TIMESTAMPTZ,
    ADD COLUMN sqs_published_at TIMESTAMPTZ;

UPDATE notifications SET sqs_publish_status = 'NOT_REQUIRED';

ALTER TABLE notifications
    ALTER COLUMN sqs_publish_status SET NOT NULL,
    ADD CONSTRAINT ck_notifications_sqs_publish_status CHECK (
        sqs_publish_status IN ('NOT_REQUIRED', 'PENDING', 'PUBLISHED', 'EXPIRED', 'FAILED')
    ),
    ADD CONSTRAINT ck_notifications_sqs_publish_timestamps CHECK (
        (sqs_publish_status = 'PENDING'
            AND next_attempt_at IS NOT NULL AND sqs_published_at IS NULL)
        OR (sqs_publish_status = 'PUBLISHED'
            AND next_attempt_at IS NULL AND sqs_published_at IS NOT NULL)
        OR (sqs_publish_status IN ('NOT_REQUIRED', 'EXPIRED', 'FAILED')
            AND next_attempt_at IS NULL AND sqs_published_at IS NULL)
    );
