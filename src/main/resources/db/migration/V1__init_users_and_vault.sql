-- Erstes Schema-Setup für Auth + Vault
-- Hinweis: Wenn CREATE EXTENSION nicht erlaubt ist, kannst du die Zeile auskommentieren.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- USERS
CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(320)  NOT NULL,
    password_hash   TEXT          NOT NULL,
    totp_secret_enc TEXT          NULL,
    totp_verified   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- E-Mail eindeutig (case-sensitive, wie in @Index auf "email")
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE indexname = 'ux_users_email'
    ) THEN
        CREATE UNIQUE INDEX ux_users_email ON users (email);
    END IF;
END$$;

-- VAULT_ITEMS (entspricht deiner Entity; falls du sie im Auth-Backend wirklich nutzt)
CREATE TABLE IF NOT EXISTS vault_items (
    id             BIGSERIAL PRIMARY KEY,
    owner_id       UUID        NOT NULL,
    title_enc      VARCHAR(1024) NOT NULL DEFAULT '',
    username_enc   VARCHAR(1024) NOT NULL DEFAULT '',
    password_enc   VARCHAR(2048) NOT NULL DEFAULT '',
    url_enc        VARCHAR(1024) NOT NULL DEFAULT '',
    notes_enc      TEXT          NOT NULL DEFAULT '',
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_vaultitem_owner
        FOREIGN KEY (owner_id) REFERENCES users (id)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE indexname = 'ix_vault_owner'
    ) THEN
        CREATE INDEX ix_vault_owner ON vault_items (owner_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE indexname = 'ix_vault_created_at'
    ) THEN
        CREATE INDEX ix_vault_created_at ON vault_items (created_at);
    END IF;
END$$;
