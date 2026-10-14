-- =====================================================================
-- JURIKA V44 (ticket-service) -- Lot L7 (E5), RG-PLN-01 : quota de dossiers
-- par mois d'abonnement.
--
-- Un dossier est compte des qu'un ticket y est ouvert dans le mois
-- d'abonnement, une seule fois quel que soit le nombre de tickets :
--   SELECT COUNT(DISTINCT dossier_id) FROM tickets
--    WHERE workspace_id = ? AND created_at >= ? AND created_at < ?
-- (PlanLimitsService, jurika-common). Le compte est fait a chaque ouverture
-- de ticket et a chaque lecture de l'usage : index sur (workspace_id,
-- created_at) pour qu'il reste borne au cabinet et au mois.
-- Aucune donnee nouvelle, aucun droit nouveau.
-- =====================================================================

CREATE INDEX IF NOT EXISTS idx_tickets_workspace_ouverture
    ON tickets (workspace_id, created_at, dossier_id);
