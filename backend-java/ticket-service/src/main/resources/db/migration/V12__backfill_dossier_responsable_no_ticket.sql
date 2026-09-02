-- =====================================================================
-- JURIKA V12 (ticket-service) — Backfill responsable_id des dossiers SANS ticket
--
-- Contexte (2026-07-03) : apres V11, il reste des dossiers a responsable_id
-- NULL -> visibles du superviseur seulement, invisibles des employes (scoping
-- Lot Q). Source confirmee : TestSeedController (dataroom-service) inserait des
-- entreprise_dossiers SANS responsable_id ET SANS creer de ticket. Le backfill
-- V11 joint sur `tickets` : il ne peut donc PAS rattraper ces orphelins.
--
-- V12 COMPLETE V11 (ne le remplace pas) : pour tout dossier encore NULL (donc
-- sans ticket), on attribue un responsable = 1er EMPLOYE actif du meme
-- workspace ; a defaut un SUPERVISEUR actif. Determinisme via created_at puis id.
--
-- Fail-closed : si un workspace n'a AUCUN employe/superviseur, le dossier reste
-- NULL (superviseur seulement) et la migration ne plante pas (garde EXISTS).
-- Idempotent : garde `responsable_id IS NULL` -> ne touche jamais un dossier
-- deja correctement attribue (V9/V11 ou creation normale).
-- =====================================================================

UPDATE entreprise_dossiers d
   SET responsable_id = (
        SELECT u.id
          FROM users u
         WHERE u.workspace_id = d.workspace_id
           AND u.is_active = TRUE
           AND u.role IN ('EMPLOYE', 'SUPERVISEUR')
         ORDER BY CASE WHEN u.role = 'EMPLOYE' THEN 0 ELSE 1 END,
                  u.created_at ASC,
                  u.id ASC
         LIMIT 1
       )
 WHERE d.responsable_id IS NULL
   AND EXISTS (
        SELECT 1
          FROM users u2
         WHERE u2.workspace_id = d.workspace_id
           AND u2.is_active = TRUE
           AND u2.role IN ('EMPLOYE', 'SUPERVISEUR')
       );
