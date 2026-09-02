ALTER TABLE social_accounts
    DROP CONSTRAINT ux_social_accounts_provider_subject,
    DROP CONSTRAINT ck_social_accounts_subject_not_blank,
    DROP COLUMN subject,
    ALTER COLUMN subject_hmac SET NOT NULL,
    ADD CONSTRAINT ux_social_accounts_provider_subject_hmac
        UNIQUE (provider, subject_hmac),
    ADD CONSTRAINT ck_social_accounts_subject_hmac_format
        CHECK (subject_hmac ~ '^[0-9a-f]{64}$');

DROP TABLE banned_social_identities;
