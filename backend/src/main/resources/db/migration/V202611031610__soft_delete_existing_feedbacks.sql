ALTER TABLE feedbacks ADD COLUMN deleted_at TIMESTAMPTZ;

-- 도입 전 피드백의 원문·작성자·접수 시각은 보존하고 사용자 고객센터 조회에서 제외한다.
UPDATE feedbacks SET deleted_at = CURRENT_TIMESTAMP;
