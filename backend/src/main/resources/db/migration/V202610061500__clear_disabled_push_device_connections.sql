ALTER TABLE push_devices
    ALTER COLUMN user_id DROP NOT NULL,
    ALTER COLUMN session_id DROP NOT NULL;

-- 이미 비활성화된 기기도 회원·로그인·토큰 연결을 제거한다.
UPDATE push_devices
SET user_id = NULL, session_id = NULL, fcm_token = NULL, fcm_token_hash = NULL
WHERE disabled_at IS NOT NULL;

ALTER TABLE push_devices
    ADD CONSTRAINT ck_push_devices_active_connection CHECK (
        disabled_at IS NOT NULL OR (user_id IS NOT NULL AND session_id IS NOT NULL)
    ),
    ADD CONSTRAINT ck_push_devices_disabled_connection CHECK (
        disabled_at IS NULL OR (user_id IS NULL AND session_id IS NULL
            AND fcm_token IS NULL AND fcm_token_hash IS NULL)
    );
