-- En-tête PDF cabinet (2026-07-14) — nom affiché en en-tête des documents générés.
--
-- Le socle (en-tête) de TOUS les PDF (Fiche client, État des débours, futurs)
-- affiche le nom du cabinet. Par défaut = dénomination du workspace
-- (workspaces.name, saisie à l'inscription). Cette colonne permet de
-- personnaliser ce nom sans toucher à la dénomination légale.
--
-- NULLABLE par conception : vide => repli automatique sur workspaces.name
-- (l'en-tête n'est jamais vide). Aucun backfill.
--
-- RLS / multitenancy : aucune incidence — `workspaces` est la table racine de
-- tenant, ajouter une colonne scalaire nullable ne touche pas les policies.
--
-- Ordre des migrations : dernière migration auth-service = V29
-- (workspace_professional_type). V30 est le numéro suivant disponible.

ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS nom_affiche_documents VARCHAR(150);

COMMENT ON COLUMN workspaces.nom_affiche_documents IS
    'En-tête PDF (2026-07-14) — nom affiché en en-tête des documents générés ; NULL/vide => repli sur workspaces.name';
