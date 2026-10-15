-- =====================================================================
-- JURIKA V47 (auth-service) -- Lot L6b, etape E2 : preferences d'affichage
-- du COMPTE (et non plus du cabinet).
--
-- Chaque utilisateur (super-admin, superviseur, employe, client) choisit :
--   theme       : 'SYSTEME' (suit le reglage du poste), 'CLAIR' ou 'SOMBRE' ;
--   juri_masque : l'assistant Juri est masque (vrai) ou affiche (faux).
-- Le choix suit le compte d'un poste a l'autre.
--
-- Colonnes de users (table existante, deja sous RLS par workspace : policy
-- user_workspace_isolation de V1, droits de jurika_app de V32) : aucune
-- nouvelle table, donc rien a ajouter a essai_supprimer_definitivement (la
-- suppression d'un essai efface deja les lignes de users du cabinet).
--
-- Valeurs par defaut pour tous les comptes existants : SYSTEME et FALSE.
-- workspaces.preferred_theme (V22, theme du CABINET, 'light'/'dark') n'est PAS
-- repris : il n'a jamais ete relu par l'interface (backlog L6, motif 9) ; le
-- copier imposerait un choix que personne n'a fait pour son compte. La colonne
-- reste en place (point d'entree /api/v1/users/me/theme conserve, deprecie).
-- =====================================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS theme VARCHAR(10) NOT NULL DEFAULT 'SYSTEME';
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_theme_check;
ALTER TABLE users ADD CONSTRAINT users_theme_check CHECK (theme IN ('SYSTEME', 'CLAIR', 'SOMBRE'));

ALTER TABLE users ADD COLUMN IF NOT EXISTS juri_masque BOOLEAN NOT NULL DEFAULT FALSE;
