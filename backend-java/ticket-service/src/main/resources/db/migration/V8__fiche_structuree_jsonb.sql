-- =====================================================================
-- JURIKA V8 (ticket-service) — Fiche structuree JSONB (2026-06-24)
--
-- Backbone de la refonte des STATUTS lors d'une MODIFICATION : la table
-- entreprise_dossiers ne porte que les champs scalaires de base
-- (raison_sociale, forme, capital, adresse, ville...). Or un statut
-- COMPLET refondu (genere depuis la fiche structuree, jamais depuis le
-- scan) a besoin de l'etat structure complet : objet social, duree,
-- gerance (noms), associes + repartition des parts, valeur nominale...
--
-- On stocke cet etat dans une colonne JSONB unique `fiche_structuree`,
-- alimentee :
--   - a la CREATION (depuis les etapes du wizard) ;
--   - a l'IMPORT (depuis la fiche juridique consolidee) ;
--   - apres chaque MODIFICATION (nouvelles valeurs persistees) ;
-- afin que les modifications successives partent d'un etat a jour.
--
-- NULLABLE + pas de DEFAULT : aucune reecriture de table, aucun impact
-- sur les lignes existantes (societes deja en base restent a NULL et
-- seront completees au 1er passage en Modification via le preflight).
--
-- RLS / multi-tenancy : la policy `dossier_isolation` (V3) est
-- row-based (filtre workspace_id) et n'est PAS affectee par l'ajout
-- d'une colonne. L'isolation reste strictement identique.
-- =====================================================================

ALTER TABLE entreprise_dossiers
    ADD COLUMN IF NOT EXISTS fiche_structuree JSONB;

COMMENT ON COLUMN entreprise_dossiers.fiche_structuree IS
    'Etat structure complet de la societe (objet, duree, gerance, associes, '
    'parts, capital, siege, denomination...). Source unique de verite pour la '
    'generation du statut refondu en MODIFICATION. Alimentee a CREATION/IMPORT '
    'et apres chaque MODIFICATION. NULL = a completer au 1er preflight.';
