-- 좋아요 마일스톤 푸시 수신 설정. 기존 회원은 켜짐으로 시작한다.
ALTER TABLE users
    ADD COLUMN like_push_enabled BOOLEAN NOT NULL DEFAULT TRUE;
