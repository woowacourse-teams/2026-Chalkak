-- 로그인별 기기의 회원 정보를 이관한 뒤 로그인 식별자 테이블을 제거한다.
ALTER TABLE push_devices ADD COLUMN user_id UUID;

UPDATE push_devices device
SET user_id = session.user_id
FROM login_sessions session
WHERE device.session_id = session.id;

ALTER TABLE push_devices
    ALTER COLUMN user_id SET NOT NULL,
    ADD CONSTRAINT fk_push_devices_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    DROP CONSTRAINT fk_push_devices_session;

ALTER TABLE user_refresh_tokens DROP CONSTRAINT fk_user_refresh_tokens_session;

DROP TABLE login_sessions;
