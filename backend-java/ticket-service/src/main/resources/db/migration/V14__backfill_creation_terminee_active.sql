-- =====================================================================
-- Lot W4 (2026-07-04) — Backfill CONDITIONNEL EN_CONSTITUTION -> ACTIVE
-- ---------------------------------------------------------------------
-- Contexte : jusqu'a ce lot, une societe creee restait EN_CONSTITUTION a
-- vie (WorkflowUseCases.createEntrepriseDossier inserait EN_CONSTITUTION en
-- dur, rien ne repassait ACTIVE ; seul l'IMPORT posait ACTIVE). Desormais
-- le workflow CREATION passe la societe ACTIVE a sa COMPLETION. Ce backfill
-- regularise les creations DEJA TERMINEES avant ce lot.
--
-- CRITERE retenu = « creation terminee » : il existe un ticket de type
-- CREATION au statut CLOTURE lie a ce dossier. C'est fiable et 100%
-- self-contained (tables ticket-service uniquement, pas de dependance
-- d'ordre Flyway cross-service) : le dossier n'est cree QU'A la completion
-- du workflow CREATION, moment ou son ticket auto-transitionne EN_COURS ->
-- CLOTURE (WORKFLOW_COMPLETED) et est lie via dossier_id.
--
-- IMPORTANT : ce N'EST PAS un UPDATE aveugle sur tous les EN_CONSTITUTION.
--   - Les creations ENCORE EN COURS n'ont pas de dossier -> rien a activer.
--   - Les stubs d'IMPORT en cours (ticket type = 'IMPORT') sont EXCLUS par
--     le filtre t.type = 'CREATION' -> ils restent EN_CONSTITUTION.
--   - DISSOUTE / EN_LIQUIDATION / LIQUIDEE / RADIE ne sont pas touches
--     (filtre statut = 'EN_CONSTITUTION').
-- Idempotent : rejouer la migration ne change plus rien (deja ACTIVE).
-- =====================================================================
UPDATE entreprise_dossiers d
SET statut = 'ACTIVE',
    updated_at = NOW()
WHERE d.statut = 'EN_CONSTITUTION'
  AND EXISTS (
        SELECT 1
        FROM tickets t
        WHERE t.dossier_id = d.id
          AND t.workspace_id = d.workspace_id
          AND t.type = 'CREATION'
          AND t.statut = 'CLOTURE'
  );
