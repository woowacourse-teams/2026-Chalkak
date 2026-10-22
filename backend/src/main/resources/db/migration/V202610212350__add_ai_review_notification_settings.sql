-- AI 리뷰 결과 푸시 수신 설정. 기존 회원은 켜짐으로 시작한다.
ALTER TABLE users
    ADD COLUMN ai_review_push_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- AI 리뷰 결과 알림은 반드시 게시물을 대상으로 한다. 다른 종류의 기존 행에는 영향이 없다.
ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_ai_review_source CHECK (
        type NOT IN ('AI_REVIEW_COMPLETED', 'AI_REVIEW_FAILED')
        OR (source_type = 'POST' AND source_id IS NOT NULL)
    );
