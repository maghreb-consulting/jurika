-- =====================================================================
-- JURIKA V21 -- Cochage des demarches par ticket
--
-- `demarches_referentiel` (V20) dit CE QU'IL FAUT FAIRE ; ces deux tables
-- disent CE QUI A ETE FAIT sur un ticket donne.
--
-- Une ligne `ticket_demarches` n'est creee qu'au premier geste de l'employe
-- (cochage ou mise hors perimetre) : l'absence de ligne vaut « a faire ». On
-- evite ainsi 36 lignes mortes par ticket et, surtout, une re-synchronisation a
-- chaque nouvelle version du guide.
-- =====================================================================

CREATE TABLE ticket_demarches (
    id           UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    ticket_id    UUID         NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    demarche_id  UUID         NOT NULL REFERENCES demarches_referentiel(id) ON DELETE RESTRICT,
    etat         VARCHAR(20)  NOT NULL DEFAULT 'A_FAIRE'
                 CHECK (etat IN ('A_FAIRE','COCHEE','NON_APPLICABLE')),
    -- Motif obligatoire pour ecarter une demarche conditionnelle ; libre sinon.
    motif        TEXT,
    acteur_id    UUID         REFERENCES users(id) ON DELETE SET NULL,
    coche_at     TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (ticket_id, demarche_id),
    -- Ecarter une demarche sans dire pourquoi n'a pas de valeur probante.
    CONSTRAINT chk_demarche_motif_non_applicable
        CHECK (etat <> 'NON_APPLICABLE'
               OR (motif IS NOT NULL AND length(btrim(motif)) > 0)),
    -- Un etat autre que « a faire » est date.
    CONSTRAINT chk_demarche_date_coherente
        CHECK (etat = 'A_FAIRE' OR coche_at IS NOT NULL)
);

CREATE INDEX idx_ticket_demarches_ticket
    ON ticket_demarches (workspace_id, ticket_id);
CREATE INDEX idx_ticket_demarches_etat
    ON ticket_demarches (workspace_id, ticket_id, etat);

-- ---------------------------------------------------------------------
-- Justificatifs REELLEMENT televerses pour une demarche donnee.
--
-- Le rattachement est explicite et non deduit du type : les etapes 13, 14 et 17
-- attendent toutes un document « STATUTS » (signe, puis legalise, puis
-- enregistre). Sans cette table, cocher l'etape 17 passerait au seul motif qu'un
-- document STATUTS existe deja depuis l'etape 13 — le controle serait vide.
--
-- FK vers dataroom_documents : les services partagent une base unique et le
-- precedent existe deja en sens inverse (dataroom_documents.ticket_id ->
-- tickets.id, migration dataroom V5).
--
-- ORDRE DE DEMARRAGE -- docker-compose n'ordonne pas ticket-service et
-- dataroom-service entre eux. Sur une base VIERGE, cette migration peut donc
-- s'executer avant dataroom V5 et echouer. Ce n'est pas bloquant : PostgreSQL
-- ayant un DDL transactionnel, Flyway annule la migration ET sa ligne
-- d'historique, et `restart: unless-stopped` relance le service jusqu'a ce que
-- dataroom V5 soit passe. C'est exactement le mecanisme dont depend deja la FK
-- inverse de dataroom V5 vers `tickets`.
--
-- ON DELETE RESTRICT : supprimer la piece qui prouve une demarche accomplie doit
-- echouer, pas passer inapercu. `DataroomJuridiqueService.delete` transforme ce
-- refus en message explicite.
-- ---------------------------------------------------------------------
CREATE TABLE ticket_demarche_justificatifs (
    id                 UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id       UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    ticket_demarche_id UUID        NOT NULL REFERENCES ticket_demarches(id) ON DELETE CASCADE,
    document_id        UUID        NOT NULL REFERENCES dataroom_documents(id) ON DELETE RESTRICT,
    document_type      VARCHAR(60) NOT NULL,
    alternative_groupe SMALLINT    NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (ticket_demarche_id, document_id)
);

CREATE INDEX idx_tdj_demarche ON ticket_demarche_justificatifs (ticket_demarche_id);
CREATE INDEX idx_tdj_document ON ticket_demarche_justificatifs (document_id);

-- ---------------------------------------------------------------------
-- RLS : meme politique que les autres tables du ticket-service.
-- (Rappel projet : la RLS est INERTE en runtime, l'application se connectant
-- avec un role superuser. Le filtrage effectif reste applicatif -- on la pose
-- neanmoins pour rester homogene et utile le jour ou le role changera.)
-- ---------------------------------------------------------------------
ALTER TABLE ticket_demarches ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_demarche_justificatifs ENABLE ROW LEVEL SECURITY;

CREATE POLICY ticket_demarches_isolation ON ticket_demarches FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

CREATE POLICY tdj_isolation ON ticket_demarche_justificatifs FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);
