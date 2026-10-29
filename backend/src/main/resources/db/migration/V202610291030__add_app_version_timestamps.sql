-- 기존 행의 실제 생성·수정 시각은 복원할 수 없으므로 마이그레이션 적용 시각으로 채운다.
ALTER TABLE app_versions
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
