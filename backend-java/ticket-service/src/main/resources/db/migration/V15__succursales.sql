-- =====================================================================
-- JURIKA V15 — Persistance des succursales (2026-07-05)
-- =====================================================================
-- CAUSE RACINE : jusqu'ici les workflows SUCCURSALE_MA / SUCCURSALE_ETR ne
-- creaient AUCUNE ligne "succursale" ; la fermeture identifiait la succursale
-- par saisie manuelle (RC + ville). Il devient donc impossible de proposer la
-- liste des succursales d'une societe mere ou d'auto-remplir a la fermeture.
--
-- Cette table appartient a ticket-service (qui possede deja entreprise_dossiers,
-- meme base Postgres partagee). workflow-service y INSERT a la completion des
-- workflows de creation (native query, comme pour entreprise_dossiers) et UPDATE
-- le statut a la fermeture. ticket-service expose la liste (endpoint scope).
--
-- LEGACY : les succursales creees AVANT ce lot n'ont jamais ete stockees -> non
-- backfillables, non listables. Le front garde un fallback saisie manuelle.
-- ---------------------------------------------------------------------
CREATE TABLE succursales (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id      UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    -- Societe mere proprietaire : dossier local (entreprise_dossiers). Pour une
    -- succursale ETR sans dossier local rattache, la ligne n'est PAS creee
    -- (contrainte NOT NULL) -> la succursale reste en saisie manuelle (legacy).
    parent_dossier_id UUID         NOT NULL REFERENCES entreprise_dossiers(id) ON DELETE CASCADE,
    type              VARCHAR(10)  NOT NULL CHECK (type IN ('MA','ETR')),
    denomination      VARCHAR(200),
    activite          TEXT,
    adresse           TEXT,
    ville             VARCHAR(100),
    rc_secondaire     VARCHAR(50),
    directeur_nom     VARCHAR(120),
    directeur_prenom  VARCHAR(120),
    directeur_cin     VARCHAR(30),
    -- ETR uniquement : pays d'origine de la societe mere etrangere. NULL pour MA.
    pays_origine      VARCHAR(100),
    statut            VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE'
                      CHECK (statut IN ('ACTIVE','FERMEE')),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    closed_at         TIMESTAMPTZ
);

CREATE INDEX idx_succursales_workspace_parent
    ON succursales (workspace_id, parent_dossier_id);

-- ---------------------------------------------------------------------
-- RLS multi-tenant — bloc COPIE a l'identique des tables existantes du service
-- (cf. V3 entreprise_dossiers). CRITIQUE : sans WITH CHECK, les INSERT depuis
-- workflow-service (session avec app.current_workspace_id positionne) seraient
-- refuses lorsque la RLS est active (jurika_user peut avoir BYPASSRLS dans le
-- conteneur, mais on ne s'appuie PAS dessus — defense-in-depth).
-- ---------------------------------------------------------------------
ALTER TABLE succursales ENABLE ROW LEVEL SECURITY;

CREATE POLICY succursale_isolation ON succursales FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);
