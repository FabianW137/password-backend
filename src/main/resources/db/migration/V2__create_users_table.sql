-- V2: Erstellt/ergänzt die users-Tabelle für die Authentifizierung
-- Idempotent ausgeführt; sicher bei wiederholtem Aufruf.

-- Für gen_random_uuid(); falls nicht erlaubt, kann diese Zeile bleiben – sie ist IF NOT EXISTS.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Tabelle anlegen, falls sie fehlt
CREATE TABLE IF NOT EXISTS users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email            VARCHAR(320)  NOT NULL,
    password_hash    TEXT          NOT NULL,
    totp_secret_enc  TEXT          NULL,
    totp_verified    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Falls alte Schemas existieren: fehlende Spalten nachziehen (idempotent)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS totp_secret_enc  TEXT,
    ADD COLUMN IF NOT EXISTS totp_verified    BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW();

-- Eindeutigkeit der E-Mail (case-insensitive) sicherstellen
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE indexname = 'ux_users_email_lower'
    ) THEN
        CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));
    END IF;
END$$;
