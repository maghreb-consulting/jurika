-- ════════════════════════════════════════════════════════════════════════════
-- Sprint 12 — alignement plan codes avec auth-service V16 workspace.selected_plan
-- ════════════════════════════════════════════════════════════════════════════
-- La source canonique des plan codes est V16 :
--   CHECK (selected_plan IN ('essentiel', 'professionnel', 'entreprise'))
-- V1 billing utilisait par erreur ('starter', 'business', 'enterprise')
-- (alignes sur les ID Stripe products, mais decales du reste de la stack).
-- On etend la CHECK pour accepter LES DEUX, et on backfill les eventuelles
-- lignes dev existantes vers la forme canonique (en pratique vide en V1 mais
-- defensive).
-- ════════════════════════════════════════════════════════════════════════════

ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS subscriptions_plan_code_check;
ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS ck_plan_code;

UPDATE subscriptions SET plan_code = 'essentiel'      WHERE plan_code = 'starter';
UPDATE subscriptions SET plan_code = 'professionnel'  WHERE plan_code = 'business';
UPDATE subscriptions SET plan_code = 'entreprise'     WHERE plan_code = 'enterprise';

ALTER TABLE subscriptions ADD CONSTRAINT ck_plan_code_canonical
    CHECK (plan_code IN ('essentiel', 'professionnel', 'entreprise'));

COMMENT ON COLUMN subscriptions.plan_code IS
    'Sprint 12 — aligne sur workspace.selected_plan (V16) : essentiel | professionnel | entreprise.';
