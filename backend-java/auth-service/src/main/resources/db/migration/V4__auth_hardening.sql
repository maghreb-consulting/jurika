-- =====================================================================
-- JURIKA -- V3 Auth Hardening (Phase 1.5)
-- Date : 2026-05-13 -- Specifications directeur Maghreb Consulting
--
-- Apporte :
--   1) Enrichissement users (twofa_method, email_verified_at, phone_e164, phone_verified_at)
--   2) Extension status workspaces (PENDING_VERIFICATION)
--   3) Table email_verification_tokens (token UUID, TTL 24h)
--   4) Table sms_otp_codes (code 6 chiffres, TTL 5min, max 5 attempts)
--   5) Table recovery_codes (10 codes BCrypt one-shot)
--   6) RLS sur les 3 nouvelles tables
--   7) Backfill : marquer les users existants comme verifies (compat seeds)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Enrichissement table users
-- ---------------------------------------------------------------------
-- (NB : must_change_password existe deja en V1, on le reutilise tel quel)

ALTER TABLE users
    ADD COLUMN twofa_method        VARCHAR(10)
        CHECK (twofa_method IS NULL OR twofa_method IN ('SMS', 'TOTP')),
    ADD COLUMN email_verified_at   TIMESTAMPTZ,
    ADD COLUMN phone_verified_at   TIMESTAMPTZ,
    ADD COLUMN phone_e164          VARCHAR(20);

CREATE INDEX idx_users_phone ON users (phone_e164) WHERE phone_e164 IS NOT NULL;

-- ---------------------------------------------------------------------
-- 2. Extension du status workspaces (ajout PENDING_VERIFICATION)
-- ---------------------------------------------------------------------
ALTER TABLE workspaces DROP CONSTRAINT IF EXISTS workspaces_status_check;
ALTER TABLE workspaces ADD CONSTRAINT workspaces_status_check
    CHECK (status IN ('ACTIVE','SUSPENDED','DEACTIVATED','PENDING_VERIFICATION'));

-- ---------------------------------------------------------------------
-- 3. email_verification_tokens
--    Token UUID SHA-256-hashed, TTL 24h, single-use
-- ---------------------------------------------------------------------
CREATE TABLE email_verification_tokens (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id         UUID         NOT NULL REFERENCES users(id)      ON DELETE CASCADE,
    token_hash      VARCHAR(120) NOT NULL UNIQUE,
    email           VARCHAR(150) NOT NULL,
    purpose         VARCHAR(30)  NOT NULL DEFAULT 'EMAIL_VERIFICATION'
                    CHECK (purpose IN ('EMAIL_VERIFICATION','EMAIL_CHANGE')),
    expires_at      TIMESTAMPTZ  NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(255)
);
CREATE INDEX idx_email_verif_user_purpose
    ON email_verification_tokens (user_id, purpose, expires_at);

-- ---------------------------------------------------------------------
-- 4. sms_otp_codes
--    Code 6 chiffres SHA-256-hashed, TTL 5min, max 5 attempts
-- ---------------------------------------------------------------------
CREATE TABLE sms_otp_codes (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id         UUID         NOT NULL REFERENCES users(id)      ON DELETE CASCADE,
    code_hash       VARCHAR(120) NOT NULL,
    phone_e164      VARCHAR(20)  NOT NULL,
    purpose         VARCHAR(30)  NOT NULL
                    CHECK (purpose IN ('PHONE_VERIFICATION','2FA_SETUP','2FA_LOGIN','PASSWORD_RESET')),
    expires_at      TIMESTAMPTZ  NOT NULL,
    attempts        SMALLINT     NOT NULL DEFAULT 0,
    max_attempts    SMALLINT     NOT NULL DEFAULT 5,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sms_otp_user_purpose ON sms_otp_codes (user_id, purpose, expires_at);
CREATE INDEX idx_sms_otp_active ON sms_otp_codes (user_id, used_at) WHERE used_at IS NULL;

-- ---------------------------------------------------------------------
-- 5. recovery_codes
--    10 codes BCrypt hashes par user, affiches UNE SEULE FOIS a la generation
-- ---------------------------------------------------------------------
CREATE TABLE recovery_codes (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id         UUID         NOT NULL REFERENCES users(id)      ON DELETE CASCADE,
    code_hash       VARCHAR(120) NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_recovery_codes_user ON recovery_codes (user_id, used_at);

-- ---------------------------------------------------------------------
-- 6. RLS sur les 3 nouvelles tables
-- ---------------------------------------------------------------------
ALTER TABLE email_verification_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE sms_otp_codes              ENABLE ROW LEVEL SECURITY;
ALTER TABLE recovery_codes             ENABLE ROW LEVEL SECURITY;

CREATE POLICY email_verif_isolation ON email_verification_tokens FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

CREATE POLICY sms_otp_isolation ON sms_otp_codes FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

CREATE POLICY recovery_codes_isolation ON recovery_codes FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

-- ---------------------------------------------------------------------
-- 7. Backfill : marquer les users existants (seed + comptes deja crees)
--    comme deja verifies pour ne pas casser leur login
-- ---------------------------------------------------------------------
UPDATE users
SET email_verified_at = COALESCE(email_verified_at, created_at)
WHERE email_verified_at IS NULL;

-- Si l'user a un phone dans la table V1 (deja existant), le copier en phone_e164
UPDATE users
SET phone_e164 = phone
WHERE phone IS NOT NULL
  AND phone_e164 IS NULL
  AND phone ~ '^\+?[0-9]{8,15}$';
