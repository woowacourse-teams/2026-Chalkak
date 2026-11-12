CREATE TABLE filters (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    artist_id UUID NOT NULL,
    name VARCHAR(30),
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    save_count BIGINT NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_filters_artist FOREIGN KEY (artist_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_filters_status CHECK (status IN ('DRAFT', 'PUBLIC', 'PRIVATE')),
    CONSTRAINT ck_filters_published CHECK ((published_at IS NULL) = (status = 'DRAFT')),
    CONSTRAINT ck_filters_delete_draft_only CHECK (deleted_at IS NULL OR status = 'DRAFT'),
    CONSTRAINT ck_filters_save_count_not_negative CHECK (save_count >= 0)
);

CREATE INDEX ix_filters_artist_created ON filters (artist_id, created_at DESC);

CREATE TABLE filter_photos (
    filter_id UUID NOT NULL,
    sort_order SMALLINT NOT NULL,
    upload_id UUID NOT NULL,
    PRIMARY KEY (filter_id, sort_order),
    CONSTRAINT fk_filter_photos_filter FOREIGN KEY (filter_id) REFERENCES filters (id) ON DELETE RESTRICT,
    CONSTRAINT fk_filter_photos_upload FOREIGN KEY (upload_id) REFERENCES post_image_uploads (id) ON DELETE RESTRICT,
    CONSTRAINT ux_filter_photos_upload UNIQUE (upload_id),
    CONSTRAINT ck_filter_photos_sort_order CHECK (sort_order BETWEEN 1 AND 5)
);
