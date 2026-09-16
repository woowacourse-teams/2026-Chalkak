-- 임시 Apple 인증 정보를 업로드 식별자가 아니라 신원(subject_hmac)으로 보관한다.
-- 업로드 식별자를 PK로 두면 신원당 한 행만 남아, 아직 폐기하지 않은 Refresh Token이
-- 재로그인 때 덮어써진다. Apple 폐기는 토큰 단위라 덮어쓴 토큰은 다시 폐기할 수 없으므로
-- 한 신원에 여러 행을 허용하고, 각 행은 정리 스케줄러가 폐기할 때까지 남긴다.
--
-- 기존 행의 subject_hmac은 복원할 수 없어 빈 테이블 기준으로 재생성한다. 남은 행이 있으면
-- 폐기하지 못한 Refresh Token이 Apple에 영구히 남고 되돌릴 수 없으므로, 조용히 지우는 대신
-- 배포를 멈춘다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pending_apple_authorizations) THEN
        RAISE EXCEPTION '폐기하지 않은 임시 Apple 인증 정보가 남아 있습니다. 정리 스케줄러가 폐기를 마친 뒤 다시 배포해 주세요.';
    END IF;
END $$;

DROP TABLE pending_apple_authorizations;

CREATE TABLE pending_apple_authorizations (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    subject_hmac VARCHAR(64) NOT NULL,
    encrypted_refresh_token VARCHAR(4096) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_pending_apple_authorizations_subject_hmac_format
        CHECK (subject_hmac ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_pending_apple_authorizations_refresh_token_not_blank
        CHECK (btrim(encrypted_refresh_token) <> '')
);

-- 로그인과 업로드 URL 발급이 신원의 만료 전 최신 행을 찾는다.
CREATE INDEX ix_pending_apple_authorizations_subject_hmac_created_at
    ON pending_apple_authorizations (subject_hmac, created_at DESC);

-- 정리 스케줄러가 만료된 행을 모아 폐기한다.
CREATE INDEX ix_pending_apple_authorizations_expires_at
    ON pending_apple_authorizations (expires_at);
