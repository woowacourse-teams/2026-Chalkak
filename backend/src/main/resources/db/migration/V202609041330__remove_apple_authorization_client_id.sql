ALTER TABLE apple_authorizations
    DROP CONSTRAINT ux_apple_authorizations_social_account_client,
    DROP CONSTRAINT ck_apple_authorizations_client_id_not_blank,
    DROP COLUMN client_id,
    ADD CONSTRAINT ux_apple_authorizations_social_account
        UNIQUE (social_account_id);
