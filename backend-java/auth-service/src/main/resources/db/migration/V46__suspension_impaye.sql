-- =====================================================================
-- JURIKA V46 (auth-service) -- Lot L7 (E8) : suspension pour impaye.
--
-- Article 4.4 du contrat d'abonnement : en l'absence de paiement 15 jours
-- apres l'echeance et apres un rappel reste sans effet, l'acces au workspace
-- est SUSPENDU. Aucune donnee n'est supprimee ; l'acces est retabli des le
-- paiement constate.
--
-- Le statut SUSPENDED existe depuis l'origine (suspension par DECISION du
-- super-admin, qui ne s'ouvre a personne). Le motif distingue les deux :
--   suspension_motif = 'IMPAYE' : suspension pour impaye (billing-service),
--                                 seul le superviseur se connecte, pour payer ;
--                                 levee par le paiement constate ;
--   suspension_motif IS NULL    : decision du super-admin (inchange).
-- L'ARCHIVAGE (ARCHIVED, V44) reste l'arret du forfait (articles 11 et 12).
--
-- Un motif n'existe que sur un workspace SUSPENDED (contrainte) : toute
-- transition qui sort de la suspension doit l'effacer, rien ne reste en
-- silence.
-- Colonnes ajoutees, aucune donnee existante modifiee (les SUSPENDED
-- existants gardent un motif nul : decision).
-- =====================================================================

ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS suspendu_le      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS suspension_motif VARCHAR(20);

ALTER TABLE workspaces DROP CONSTRAINT IF EXISTS workspaces_suspension_motif_check;
ALTER TABLE workspaces ADD CONSTRAINT workspaces_suspension_motif_check
    CHECK (suspension_motif IS NULL OR (suspension_motif = 'IMPAYE' AND status = 'SUSPENDED'));

COMMENT ON COLUMN workspaces.suspendu_le IS
    'Lot L7 (V46) -- debut de la suspension en cours (impaye ou decision) ; NULL hors suspension.';
COMMENT ON COLUMN workspaces.suspension_motif IS
    'Lot L7 (V46) -- IMPAYE : suspension pour impaye (article 4.4 du contrat, superviseur seul, levee au paiement constate) ; NULL : decision du super-admin.';
