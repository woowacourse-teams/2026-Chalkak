ALTER TABLE push_deliveries DROP CONSTRAINT ck_push_deliveries_lease;
ALTER TABLE push_deliveries DROP CONSTRAINT ck_push_deliveries_status;

-- 이전 구현의 진행 중 기록도 SQS 재전달에서 기기 결과를 확인해 이어 처리한다.
UPDATE push_deliveries SET status = 'PENDING' WHERE status = 'PROCESSING';

ALTER TABLE push_deliveries DROP COLUMN lease_token;
ALTER TABLE push_deliveries DROP COLUMN lease_until;
ALTER TABLE push_deliveries ADD CONSTRAINT ck_push_deliveries_status
    CHECK (status IN ('PENDING', 'COMPLETED'));
