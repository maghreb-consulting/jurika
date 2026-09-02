-- Sprint 11 TASK 2 — Extension du schema workspaces pour le trial 14 jours +
-- les identifiants legaux Maroc (ICE/IF/RC/ville) collectes au wizard signup.
--
-- Numerotation : V16 (gap V9-V15 dans auth-service accepte par Flyway)
-- pour coherence avec la convention cross-module du projet (dashboard-service
-- a deja sa propre V16__dashboard_snapshots.sql, modules independants).
--
-- DECISION 3 tranchee : 1 seule migration atomique (pas de V20 separee) car
-- l'onboarding cabinet RG-SU04 est un changement logiquement unitaire.
--
-- DECISION user "Contraintes NOT NULL uniquement sur les workspaces crees
-- post-Sprint 11" : on ajoute tout en NULLABLE + colonne tracker
-- `created_via_sprint11_wizard BOOLEAN DEFAULT FALSE` pour discrimination.
-- Les workspaces existants ont `created_via_sprint11_wizard = FALSE` et
-- toutes les nouvelles colonnes a NULL (legitime). Le use case Sprint 11
-- garantit non-nullite cote applicatif quand TRUE.

ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS trial_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS trial_ends_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS trial_status     VARCHAR(20),
    ADD COLUMN IF NOT EXISTS selected_plan    VARCHAR(20),
    ADD COLUMN IF NOT EXISTS ice              VARCHAR(15),
    ADD COLUMN IF NOT EXISTS if_fiscal        VARCHAR(8),
    ADD COLUMN IF NOT EXISTS rc_number        VARCHAR(20),
    ADD COLUMN IF NOT EXISTS city             VARCHAR(80),
    ADD COLUMN IF NOT EXISTS created_via_sprint11_wizard BOOLEAN NOT NULL DEFAULT FALSE;

-- Check : trial_status enum (NULL toleree pour workspaces non-trial)
ALTER TABLE workspaces DROP CONSTRAINT IF EXISTS workspaces_trial_status_check;
ALTER TABLE workspaces ADD CONSTRAINT workspaces_trial_status_check
    CHECK (trial_status IS NULL OR trial_status IN ('TRIAL_ACTIVE', 'TRIAL_EXPIRED', 'CONVERTED', 'CANCELLED'));

-- Check : selected_plan enum (NULL toleree pour workspaces non-trial)
ALTER TABLE workspaces DROP CONSTRAINT IF EXISTS workspaces_selected_plan_check;
ALTER TABLE workspaces ADD CONSTRAINT workspaces_selected_plan_check
    CHECK (selected_plan IS NULL OR selected_plan IN ('essentiel', 'professionnel', 'entreprise'));

-- Check : ICE = 15 digits si non-null
ALTER TABLE workspaces DROP CONSTRAINT IF EXISTS workspaces_ice_format_check;
ALTER TABLE workspaces ADD CONSTRAINT workspaces_ice_format_check
    CHECK (ice IS NULL OR ice ~ '^\d{15}$');

-- Check : IF = 8 digits si non-null
ALTER TABLE workspaces DROP CONSTRAINT IF EXISTS workspaces_if_format_check;
ALTER TABLE workspaces ADD CONSTRAINT workspaces_if_format_check
    CHECK (if_fiscal IS NULL OR if_fiscal ~ '^\d{8}$');

-- Index pour le scheduler trial-expiration (TASK 3) : scan rapide
-- des workspaces TRIAL_ACTIVE dont trial_ends_at est passe.
CREATE INDEX IF NOT EXISTS idx_workspaces_trial_ends_at
    ON workspaces (trial_ends_at)
    WHERE trial_status = 'TRIAL_ACTIVE';

CREATE INDEX IF NOT EXISTS idx_workspaces_trial_status ON workspaces (trial_status);
CREATE INDEX IF NOT EXISTS idx_workspaces_ice         ON workspaces (ice) WHERE ice IS NOT NULL;

COMMENT ON COLUMN workspaces.trial_started_at IS 'Sprint 11 — instant de creation du workspace en trial (RG-SU01)';
COMMENT ON COLUMN workspaces.trial_ends_at IS 'Sprint 11 — trial_started_at + 14 jours calendaires';
COMMENT ON COLUMN workspaces.trial_status IS 'Sprint 11 — TRIAL_ACTIVE / TRIAL_EXPIRED / CONVERTED / CANCELLED';
COMMENT ON COLUMN workspaces.selected_plan IS 'Sprint 11 — plan preselectionne au signup, modifiable Sprint 12 billing';
COMMENT ON COLUMN workspaces.ice IS 'Sprint 11 — Identifiant Commun de l Entreprise (RGS Maroc, 15 chiffres)';
COMMENT ON COLUMN workspaces.if_fiscal IS 'Sprint 11 — Identifiant Fiscal DGI Maroc (8 chiffres)';
COMMENT ON COLUMN workspaces.rc_number IS 'Sprint 11 — Numero de Registre du Commerce';
COMMENT ON COLUMN workspaces.city IS 'Sprint 11 — Ville du siege social';
COMMENT ON COLUMN workspaces.created_via_sprint11_wizard IS 'Sprint 11 — TRUE = signup self-service depuis jurika.ai (champs ICE/IF/RC/city renseignes)';
