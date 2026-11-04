ALTER TABLE feedbacks ADD COLUMN deleted_at TIMESTAMPTZ;

-- 트랜잭션 시작 시각을 고정 기준으로 사용한다. 잠금 대기·배포 중 접수도 기준 이후면 보존한다.
UPDATE feedbacks
SET deleted_at = transaction_timestamp()
WHERE created_at < transaction_timestamp();
