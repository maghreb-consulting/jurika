-- ════════════════════════════════════════════════════════════════════════════
-- Spec directeur 2026-06-02 — renommage canonique du code plan
--   AVANT : 'essentiel' | 'professionnel' | 'entreprise'  (V16)
--   APRES : 'essentiel' | 'business'      | 'entreprise'
--
-- Justification : alignement avec la marque commerciale "Business" et purge
-- du nom 'professionnel' qui creait 3 nomenclatures parallels en code/DB/UX.
-- Cf. SPEC_TARIFS_CANONIQUE.md et PROMPT_CLAUDE_CODE_UNIFY_TARIFS.md.
--
-- Strategie :
--  1. Drop l'ancienne CHECK constraint (V16 acceptait 'professionnel').
--  2. UPDATE les lignes existantes : professionnel -> business.
--  3. Re-pose CHECK constraint canonique.
-- ════════════════════════════════════════════════════════════════════════════

-- 1. Drop CHECK
ALTER TABLE workspaces DROP CONSTRAINT IF EXISTS workspaces_selected_plan_check;

-- 2. Migration data
UPDATE workspaces
   SET selected_plan = 'business'
 WHERE selected_plan = 'professionnel';

-- 3. CHECK canonique (NULL toleree -- workspaces legacy pre-Sprint 11)
ALTER TABLE workspaces ADD CONSTRAINT workspaces_selected_plan_check
    CHECK (selected_plan IS NULL OR selected_plan IN ('essentiel', 'business', 'entreprise'));

COMMENT ON COLUMN workspaces.selected_plan IS
    'Sprint 11 V16 + spec 2026-06-02 — plan tarifaire canonique : essentiel | business | entreprise (NULL = legacy pre-wizard).';
