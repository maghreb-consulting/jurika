-- ════════════════════════════════════════════════════════════════════════════
-- Spec directeur 2026-06-02 — renommage canonique du code plan billing
--   AVANT : 'essentiel' | 'professionnel' | 'entreprise'  (V2)
--   APRES : 'essentiel' | 'business'      | 'entreprise'
--
-- Symetrique de auth-service V23. Aligne subscriptions.plan_code sur la
-- spec directeur — purge du nom 'professionnel' en DB billing.
-- ════════════════════════════════════════════════════════════════════════════

-- 1. Drop CHECK existante (V2 acceptait 'professionnel')
ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS ck_plan_code_canonical;
ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS subscriptions_plan_code_check;
ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS ck_plan_code;

-- 2. Migration data
UPDATE subscriptions
   SET plan_code = 'business'
 WHERE plan_code = 'professionnel';

-- 3. CHECK canonique
ALTER TABLE subscriptions ADD CONSTRAINT ck_plan_code_canonical
    CHECK (plan_code IN ('essentiel', 'business', 'entreprise'));

COMMENT ON COLUMN subscriptions.plan_code IS
    'Spec 2026-06-02 — aligne sur workspace.selected_plan (auth V23) : essentiel | business | entreprise.';
