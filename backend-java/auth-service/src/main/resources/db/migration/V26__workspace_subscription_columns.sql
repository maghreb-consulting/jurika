-- ════════════════════════════════════════════════════════════════════════════
-- Sprint 12 finition (2026-06-04) — colonnes subscription_* sur workspaces.
--
-- Contexte : `InternalWorkspaceStatusController.updateStatus()` ne persistait
-- rien jusqu'ici (TODO Sprint 13 dans le code). Conséquence : un webhook
-- `checkout.session.completed` arrivait jusqu'à auth-service, mais le
-- workspace restait en trial — le bandeau TRIAL_ACTIVE ne se levait jamais
-- côté UI, et `RemoteTrialAccessChecker` ne pouvait jamais mapper subscription
-- vers CONVERTED faute de colonnes.
--
-- Cette migration ajoute les 4 colonnes attendues par le flow billing :
--   - subscription_status   : active | past_due | cancelled | incomplete | unpaid
--   - subscription_ends_at  : current_period_end Stripe (pour RG-BL07 grace period)
--   - active_since          : 1ʳᵉ activation (audit + dashboards superadmin)
--   - cancelled_at          : date d'annulation effective (audit + dashboards)
--
-- Idempotent (IF NOT EXISTS) pour pouvoir rejouer en dev sans casser.
-- Le `trial_status='CONVERTED'` reste positionné par le controller lors de
-- la transition ACTIVATED, en cohérence avec `RemoteTrialAccessChecker.mapStatus`.
-- ════════════════════════════════════════════════════════════════════════════

ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS subscription_status  VARCHAR(20),
    ADD COLUMN IF NOT EXISTS subscription_ends_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS active_since         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_at         TIMESTAMPTZ;

ALTER TABLE workspaces DROP CONSTRAINT IF EXISTS workspaces_subscription_status_check;
ALTER TABLE workspaces ADD CONSTRAINT workspaces_subscription_status_check
    CHECK (subscription_status IS NULL OR subscription_status IN (
        'active', 'past_due', 'cancelled', 'incomplete', 'incomplete_expired', 'unpaid', 'trialing'
    ));

CREATE INDEX IF NOT EXISTS idx_workspaces_subscription_status
    ON workspaces (subscription_status)
    WHERE subscription_status IS NOT NULL;

COMMENT ON COLUMN workspaces.subscription_status IS 'Sprint 12 finition — miroir local du subscription Stripe (active / past_due / cancelled / …). Mis à jour par InternalWorkspaceStatusController.updateStatus appelé par billing-service après webhook.';
COMMENT ON COLUMN workspaces.subscription_ends_at IS 'Sprint 12 finition — current_period_end Stripe. Sert à la grace period RG-BL07.';
COMMENT ON COLUMN workspaces.active_since IS 'Sprint 12 finition — première transition trial → active (audit / dashboards).';
COMMENT ON COLUMN workspaces.cancelled_at IS 'Sprint 12 finition — instant de la dernière transition vers cancelled.';
