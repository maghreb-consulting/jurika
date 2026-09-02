-- ════════════════════════════════════════════════════════════════════════════
-- Récupération post-divergence : restauration des colonnes trial sur workspaces.
--
-- Contexte : la migration V23 obsolète "drop trial and demo leads" (jamais
-- présente sur main, mais appliquée à une DB de dev) avait droppé les
-- colonnes trial_*, ainsi que la table marketing_leads (recréée en V24).
-- On restaure ici les colonnes attendues par WorkspaceEntity. La V16 reste
-- marquée success=t donc ne se rejouera pas — d'où cette V25.
--
-- Idempotent (IF NOT EXISTS / IF EXISTS) pour rester compatible avec les
-- DB où V16 est encore intacte (no-op total).
--
-- NB : la CHECK selected_plan est posée en V23__rename_plan_professionnel_to_business
-- avec le wording canonique (essentiel | business | entreprise), donc on
-- ne re-pose pas ici la version 'professionnel' historique de V16.
-- ════════════════════════════════════════════════════════════════════════════

ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS trial_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS trial_ends_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS trial_status     VARCHAR(20);

ALTER TABLE workspaces DROP CONSTRAINT IF EXISTS workspaces_trial_status_check;
ALTER TABLE workspaces ADD CONSTRAINT workspaces_trial_status_check
    CHECK (trial_status IS NULL OR trial_status IN ('TRIAL_ACTIVE', 'TRIAL_EXPIRED', 'CONVERTED', 'CANCELLED'));

CREATE INDEX IF NOT EXISTS idx_workspaces_trial_ends_at
    ON workspaces (trial_ends_at)
    WHERE trial_status = 'TRIAL_ACTIVE';

CREATE INDEX IF NOT EXISTS idx_workspaces_trial_status ON workspaces (trial_status);

COMMENT ON COLUMN workspaces.trial_started_at IS 'Sprint 11 — instant de creation du workspace en trial (RG-SU01). Recréée V25.';
COMMENT ON COLUMN workspaces.trial_ends_at    IS 'Sprint 11 — trial_started_at + 14 jours calendaires. Recréée V25.';
COMMENT ON COLUMN workspaces.trial_status     IS 'Sprint 11 — TRIAL_ACTIVE / TRIAL_EXPIRED / CONVERTED / CANCELLED. Recréée V25.';
