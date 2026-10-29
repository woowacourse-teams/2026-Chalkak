CREATE TABLE app_versions (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    platform VARCHAR(7) NOT NULL,
    minimum_supported_version TEXT NOT NULL,
    store_url TEXT NOT NULL,
    CONSTRAINT uq_app_versions_platform UNIQUE (platform),
    CONSTRAINT ck_app_versions_platform CHECK (platform IN ('ANDROID', 'IOS')),
    CONSTRAINT ck_app_versions_minimum_supported_version
        CHECK (minimum_supported_version ~ '^[0-9]+\.[0-9]+\.[0-9]+$'),
    CONSTRAINT ck_app_versions_store_url_not_blank CHECK (btrim(store_url) <> '')
);

-- 버전과 스토어 주소는 관리자가 입력한다. 예시 값을 초기 운영 데이터로 넣지 않는다.
