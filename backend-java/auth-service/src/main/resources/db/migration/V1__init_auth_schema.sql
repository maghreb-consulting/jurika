-- =====================================================================
-- JURIKA — Schema initial : workspaces, subscriptions, users, refresh_tokens
-- Multi-tenancy : Row Level Security via app.current_workspace_id
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. SUBSCRIPTIONS
-- ---------------------------------------------------------------------
CREATE TABLE subscriptions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    plan            VARCHAR(20)  NOT NULL CHECK (plan IN ('STARTER','PRO','ENTERPRISE')),
    storage_quota_mb  INTEGER    NOT NULL,
    max_employees   INTEGER      NOT NULL,
    max_clients     INTEGER      NOT NULL,
    monthly_price_mad NUMERIC(10,2) NOT NULL,
    starts_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ends_at         TIMESTAMPTZ,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','SUSPENDED','EXPIRED','CANCELLED')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------
-- 2. WORKSPACES (cabinets juridiques — multi-tenant root)
-- ---------------------------------------------------------------------
CREATE TABLE workspaces (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code            VARCHAR(12)  NOT NULL UNIQUE,
    name            VARCHAR(150) NOT NULL,
    legal_form      VARCHAR(50),
    address         VARCHAR(255),
    city            VARCHAR(100),
    phone           VARCHAR(30),
    contact_email   VARCHAR(150) NOT NULL,
    subscription_id UUID         REFERENCES subscriptions(id) ON DELETE SET NULL,
    storage_used_mb INTEGER      NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','SUSPENDED','DEACTIVATED')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT workspace_code_format CHECK (code ~ '^JUR-[A-Z0-9]{5}$')
);
CREATE INDEX idx_workspaces_code ON workspaces (code);
CREATE INDEX idx_workspaces_status ON workspaces (status);

-- ---------------------------------------------------------------------
-- 3. USERS
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id          UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    email                 VARCHAR(150) NOT NULL,
    password_hash         VARCHAR(72)  NOT NULL,
    first_name            VARCHAR(80)  NOT NULL,
    last_name             VARCHAR(80)  NOT NULL,
    phone                 VARCHAR(30),
    role                  VARCHAR(20)  NOT NULL
                          CHECK (role IN ('SUPER_ADMIN','SUPERVISEUR','EMPLOYE','CLIENT')),
    totp_secret_encrypted TEXT,
    totp_enabled          BOOLEAN      NOT NULL DEFAULT FALSE,
    totp_recovery_codes   TEXT,
    must_change_password  BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_attempts SMALLINT     NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_workspace_email UNIQUE (workspace_id, email)
);
CREATE INDEX idx_users_workspace ON users (workspace_id);
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_role ON users (workspace_id, role);

-- ---------------------------------------------------------------------
-- 4. REFRESH_TOKENS
-- ---------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id         UUID         NOT NULL REFERENCES users(id)      ON DELETE CASCADE,
    token_hash      VARCHAR(120) NOT NULL UNIQUE,
    issued_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ  NOT NULL,
    revoked_at      TIMESTAMPTZ,
    user_agent      VARCHAR(255),
    ip_address      VARCHAR(45)
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

-- ---------------------------------------------------------------------
-- 5. PASSWORD_RESET_TOKENS
-- ---------------------------------------------------------------------
CREATE TABLE password_reset_tokens (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id         UUID         NOT NULL REFERENCES users(id)      ON DELETE CASCADE,
    token_hash      VARCHAR(120) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ  NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_password_reset_user ON password_reset_tokens (user_id);

-- ---------------------------------------------------------------------
-- 6. AUDIT_LOG (CNDP Loi 09-08 : tracabilite des actions sensibles)
-- ---------------------------------------------------------------------
-- audit_log est une table PARTAGEE (plusieurs services l'alimentent, cf. ai V13, ticket V5,
-- dataroom V9, auth V8). Sa creation doit etre idempotente : selon l'ordre de demarrage, un
-- autre service peut l'avoir deja creee. On aligne V1 sur la convention IF NOT EXISTS du reste
-- du corpus (auth reste le proprietaire du schema racine, cf. CLAUDE.md).
CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    UUID,
    user_id         UUID,
    action          VARCHAR(80)  NOT NULL,
    entity_type     VARCHAR(80),
    entity_id       UUID,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(255),
    metadata        JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_audit_log_workspace ON audit_log (workspace_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_user      ON audit_log (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_action    ON audit_log (action, created_at DESC);

-- ---------------------------------------------------------------------
-- 7. ROW LEVEL SECURITY (multi-tenancy)
-- ---------------------------------------------------------------------
-- Convention : chaque connexion definit `SET LOCAL app.current_workspace_id = '<uuid>';`
-- Les politiques RLS filtrent automatiquement par workspace_id.

ALTER TABLE users               ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens      ENABLE ROW LEVEL SECURITY;
ALTER TABLE password_reset_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log           ENABLE ROW LEVEL SECURITY;

-- workspaces : non filtree par RLS (la table racine), mais protegee par requete applicative
ALTER TABLE workspaces          ENABLE ROW LEVEL SECURITY;

CREATE POLICY workspace_self_access ON workspaces
    FOR ALL
    USING (id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

CREATE POLICY user_workspace_isolation ON users
    FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

CREATE POLICY refresh_token_isolation ON refresh_tokens
    FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

CREATE POLICY password_reset_isolation ON password_reset_tokens
    FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

DROP POLICY IF EXISTS audit_log_isolation ON audit_log;
CREATE POLICY audit_log_isolation ON audit_log
    FOR ALL
    USING (workspace_id IS NULL OR workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id IS NULL OR workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

-- Le SUPER_ADMIN bypasse les policies (le service auth-service positionne app.current_workspace_id explicitement quand admin).
-- Avantage : pas de role superuser cote DB, l'isolation est portee par l'application.

-- ---------------------------------------------------------------------
-- 8. TRIGGER updated_at automatique
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trg_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_workspaces_updated_at  BEFORE UPDATE ON workspaces      FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
CREATE TRIGGER trg_users_updated_at       BEFORE UPDATE ON users           FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
CREATE TRIGGER trg_subscriptions_updated_at BEFORE UPDATE ON subscriptions FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
