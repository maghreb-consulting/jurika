-- Papier à en-tête cabinet (2026-07-14) — coordonnées + logo pour le socle PDF.
--
-- Complète le profil workspace pour constituer un vrai papier à en-tête :
--   * coordonnées : adresse, téléphone, site web (l'e-mail = contact_email
--     existe déjà ; l'ICE, le RC (rc_number) et l'IF (if_fiscal) existent déjà) ;
--   * logo : stocké EN BASE (colonnes logo_bytes / logo_content_type), pas dans
--     MinIO. Décision architecture : base partagée -> dataroom-service et
--     ticket-service lisent déjà `workspaces` via des vues read-only et peuvent
--     donc lire le logo sans câbler de client objet ni d'appel HTTP inter-service.
--     Le logo est unique par cabinet, PNG/JPG, ~1 Mo max : un BYTEA convient.
--
-- Toutes NULLABLES : chaque élément du papier à en-tête est optionnel (la mise
-- en page ne se dégrade jamais ; le nom du cabinet reste toujours présent).
--
-- RLS / multitenancy : `workspaces` est la table racine de tenant — colonnes
-- scalaires nullables, aucune incidence sur les policies.
--
-- Ordre des migrations : dernière migration auth-service = V30
-- (workspace_document_display_name). V31 est le numéro suivant disponible.

ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS adresse           VARCHAR(300),
    ADD COLUMN IF NOT EXISTS telephone         VARCHAR(30),
    ADD COLUMN IF NOT EXISTS site_web          VARCHAR(200),
    ADD COLUMN IF NOT EXISTS logo_content_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS logo_bytes        BYTEA;

COMMENT ON COLUMN workspaces.logo_bytes IS
    'Papier à en-tête (2026-07-14) — logo du cabinet (PNG/JPG, ~1 Mo max), rendu en en-tête des PDF ; NULL = pas de logo';
