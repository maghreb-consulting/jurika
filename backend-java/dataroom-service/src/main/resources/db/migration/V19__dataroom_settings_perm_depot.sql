-- =====================================================================
-- JURIKA V19 -- Data Room : permission "Depot" (perm_depot)
--
-- Decision 2026-06-30 : les permissions client cessent d'etre cosmetiques.
-- On ajoute un 3e niveau assignable par l'employe : DEPOT (le client peut /
-- ne peut pas DEPOSER des documents comptables).
--
-- Defaut = FALSE -> par defaut un client est en CONSULTATION SEULE (coherent
-- avec la regle "le client n'a acces qu'a son Data Room selon permissions").
-- L'enforcement reel se fait cote backend (ClientDataroomPermissionGuard) en
-- plus du RBAC par role.
-- =====================================================================

ALTER TABLE dataroom_settings
    ADD COLUMN perm_depot BOOLEAN NOT NULL DEFAULT FALSE;
