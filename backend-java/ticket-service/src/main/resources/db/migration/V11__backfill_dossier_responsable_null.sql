-- =====================================================================
-- JURIKA V11 (ticket-service) — Backfill responsable_id manquants
--
-- Contexte (2026-07-03) : apres le scoping Lot Q (EMPLOYE ne voit que les
-- dossiers ou responsable_id = userId), des dossiers ont disparu pour leur
-- employe. Cause racine : la voie de creation via le workflow CREATION
-- (workflow-service WorkflowUseCases.createEntrepriseDossier) omettait
-- responsable_id dans son INSERT natif -> les dossiers crees par le wizard
-- naissaient avec responsable_id NULL -> invisibles cote employe.
--
-- Le fix de code (renseignement de responsable_id a l'INSERT) couvre les
-- creations futures. Cette migration rattrape les dossiers deja crees entre
-- le deploiement de V9 et ce fix, qui restent a responsable_id NULL.
--
-- Regle identique au backfill V9 (miroir) : par dossier, on retient l'assigne
-- du ticket OUVERT (NOUVEAU/EN_COURS) le plus recent ; a defaut l'assigne du
-- ticket le plus recent tout statut ; a defaut le createur (cree_par_id).
-- COALESCE couvre le ticket non assigne. Idempotent : ne touche que les
-- lignes encore NULL.
-- =====================================================================

UPDATE entreprise_dossiers d
   SET responsable_id = sub.responsable
  FROM (
        SELECT DISTINCT ON (t.dossier_id)
               t.dossier_id,
               COALESCE(t.assigne_id, t.cree_par_id) AS responsable
          FROM tickets t
         WHERE t.dossier_id IS NOT NULL
         ORDER BY t.dossier_id,
                  CASE WHEN t.statut IN ('NOUVEAU','EN_COURS') THEN 0 ELSE 1 END,
                  t.created_at DESC
       ) sub
 WHERE d.id = sub.dossier_id
   AND d.responsable_id IS NULL;
