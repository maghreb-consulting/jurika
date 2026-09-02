-- =====================================================================
-- BUG 6 (2026-06-07) — Statut utilisateur a 3 etats : PENDING / ACTIVE / INACTIVE.
--
-- Avant : seul `is_active` boolean — pas de distinction entre "invite jamais
-- connecte" et "compte normalement actif", et pas de moyen pour le superviseur
-- de tracer une desactivation explicite (vs un simple verrou temporaire).
--
-- Apres : colonne `status` source de verite avec contrainte CHECK + index par
-- workspace. `is_active` est conservee + maintenue automatiquement via trigger
-- pour ne casser aucune lecture existante (LoginUseCase, JPA UserEntity,
-- requetes legacy). Convention :
--   - PENDING  = invite (employe ou client) qui n'a jamais valide sa 1ere
--                connexion -- transition automatique vers ACTIVE par
--                LoginUseCase apres 1er authentificate.
--   - ACTIVE   = compte normal, autorise a se connecter.
--   - INACTIVE = desactive explicitement par le superviseur (bloque le login,
--                ne consomme plus de quota plan).
-- =====================================================================

ALTER TABLE users
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','INACTIVE','PENDING'));

-- Backfill coherent avec l'etat existant :
--   - is_active=FALSE                                  -> INACTIVE
--   - must_change_password=TRUE AND last_login_at NULL -> PENDING  (invite jamais venu)
--   - sinon                                            -> ACTIVE   (deja le defaut)
UPDATE users SET status = 'INACTIVE' WHERE is_active = FALSE;
UPDATE users SET status = 'PENDING'
    WHERE is_active = TRUE
      AND must_change_password = TRUE
      AND last_login_at IS NULL;

CREATE INDEX idx_users_workspace_status ON users (workspace_id, status);

-- Trigger : synchronise `is_active` a partir du `status` pour ne casser AUCUNE
-- lecture preexistante (LoginUseCase verifie user.active(), PlanLimitsService
-- pourra etre mis a jour mais d'autres requetes SQL ad-hoc peuvent encore
-- s'appuyer sur is_active). PENDING et ACTIVE -> is_active=TRUE.
CREATE OR REPLACE FUNCTION trg_users_sync_is_active() RETURNS TRIGGER AS $$
BEGIN
    NEW.is_active := (NEW.status <> 'INACTIVE');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_sync_is_active
    BEFORE INSERT OR UPDATE OF status ON users
    FOR EACH ROW EXECUTE FUNCTION trg_users_sync_is_active();
