-- =====================================================================
-- JURIKA billing V13 -- Lot L7, etape E6 : contrat d'abonnement (CDC 4.8,
-- RG-CTR-01 a RG-CTR-09).
--
-- Pieces : conditions particulieres (une ligne par souscription, changement de
-- forfait ou de rythme), conditions generales et accord de traitement des
-- donnees (versions du modele de contrat, toutes conservees, RG-CTR-07).
--
-- Preuve de l'acceptation (RG-CTR-02) : contrat_acceptations, ecrite une fois,
-- jamais modifiee ni supprimee. Deux verrous :
--   1. un declencheur refuse UPDATE et DELETE (il protege aussi le role
--      proprietaire, qui migre et sous lequel billing se connecte encore) ;
--   2. le role applicatif jurika_app n'a que SELECT et INSERT (REVOKE explicite).
-- Meme regime pour les versions du modele, les resiliations, les
-- renouvellements et le journal des avis : l'historique du contrat est conserve
-- au moins dix ans (RG-CTR-09) ; aucune suppression n'est prevue.
--
-- Conditions particulieres : seuls l'etat, sa date et l'echeance courante
-- (renouvellement) changent ; les termes acceptes (forfait, prix, dates...) sont
-- figes par un declencheur.
--
-- Isolation : RLS par workspace (app.current_workspace_id) ET filtrage
-- applicatif explicite. Les lectures transverses (super-admin, taches
-- planifiees, controle d'acces de la passerelle) posent, dans leur transaction,
-- app.contrat_portee = 'globale' (set_config local a la transaction).
-- =====================================================================

-- Modele de contrat (presentation, A, B, C) : une ligne par version.
CREATE TABLE IF NOT EXISTS contrat_versions (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code                  VARCHAR(40)  NOT NULL UNIQUE CHECK (code ~ '^[0-9A-Za-z][0-9A-Za-z._-]{0,39}$'),
    texte_modele          TEXT         NOT NULL CHECK (length(texte_modele) > 0),
    empreinte_modele      CHAR(64)     NOT NULL CHECK (empreinte_modele ~ '^[0-9a-f]{64}$'),
    empreinte_cg          CHAR(64)     NOT NULL CHECK (empreinte_cg ~ '^[0-9a-f]{64}$'),
    empreinte_dpa         CHAR(64)     NOT NULL CHECK (empreinte_dpa ~ '^[0-9a-f]{64}$'),
    entree_en_vigueur_le  DATE         NOT NULL,
    origine               VARCHAR(20)  NOT NULL CHECK (origine IN ('INITIALE', 'SUPER_ADMIN')),
    publiee_par           UUID,
    publiee_le            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE SEQUENCE IF NOT EXISTS conditions_particulieres_numero;

CREATE TABLE IF NOT EXISTS conditions_particulieres (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id        UUID           NOT NULL,
    reference           VARCHAR(30)    NOT NULL UNIQUE,
    forfait             VARCHAR(20)    NOT NULL CHECK (forfait IN ('ESSENTIEL', 'PROFESSIONNEL', 'MAX', 'SUR_DEVIS')),
    rythme_paiement     VARCHAR(10)    NOT NULL CHECK (rythme_paiement IN ('MENSUEL', 'ANNUEL')),
    prix_ht             NUMERIC(12,2)  NOT NULL CHECK (prix_ht > 0),
    date_debut          DATE           NOT NULL,
    date_fin            DATE           NOT NULL CHECK (date_fin > date_debut),
    mode_paiement       VARCHAR(20)    CHECK (mode_paiement IN ('CARTE', 'VIREMENT', 'CHEQUE', 'ESPECES')),
    origine             VARCHAR(20)    NOT NULL CHECK (origine IN ('SUPER_ADMIN', 'ABONNEMENT')),
    statut              VARCHAR(20)    NOT NULL DEFAULT 'EN_ATTENTE'
                        CHECK (statut IN ('EN_ATTENTE', 'ACCEPTE', 'REMPLACE', 'ANNULE', 'RESILIE')),
    echeance_courante   DATE           NOT NULL,
    cree_par            UUID,
    cree_le             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    statut_modifie_le   TIMESTAMPTZ
);

-- Un seul contrat en attente et un seul contrat accepte par cabinet a la fois.
CREATE UNIQUE INDEX IF NOT EXISTS uq_conditions_une_en_attente
    ON conditions_particulieres (workspace_id) WHERE statut = 'EN_ATTENTE';
CREATE UNIQUE INDEX IF NOT EXISTS uq_conditions_une_acceptee
    ON conditions_particulieres (workspace_id) WHERE statut = 'ACCEPTE';
CREATE INDEX IF NOT EXISTS idx_conditions_workspace
    ON conditions_particulieres (workspace_id, cree_le DESC);

-- Preuve de l'acceptation (RG-CTR-02).
CREATE TABLE IF NOT EXISTS contrat_acceptations (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id           UUID          NOT NULL,
    conditions_id          UUID          NOT NULL REFERENCES conditions_particulieres(id),
    version_id             UUID          NOT NULL REFERENCES contrat_versions(id),
    version_code           VARCHAR(40)   NOT NULL,
    objet                  VARCHAR(30)   NOT NULL CHECK (objet IN ('CONDITIONS_PARTICULIERES', 'NOUVELLE_VERSION')),
    texte_accepte          TEXT          NOT NULL CHECK (length(texte_accepte) > 0),
    empreinte_contrat      CHAR(64)      NOT NULL CHECK (empreinte_contrat ~ '^[0-9a-f]{64}$'),
    empreinte_cg           CHAR(64)      NOT NULL,
    empreinte_dpa          CHAR(64)      NOT NULL,
    signataire_user_id     UUID          NOT NULL,
    signataire_compte      VARCHAR(200)  NOT NULL,
    signataire_civilite    VARCHAR(10)   NOT NULL,
    signataire_nom         VARCHAR(120)  NOT NULL,
    signataire_prenom      VARCHAR(120)  NOT NULL,
    signataire_qualite     VARCHAR(120)  NOT NULL CHECK (length(btrim(signataire_qualite)) > 0),
    signataire_role        VARCHAR(20)   NOT NULL CHECK (signataire_role = 'SUPERVISEUR'),
    cabinet_denomination   VARCHAR(200)  NOT NULL,
    cabinet_email          VARCHAR(200),
    cabinet_forme          VARCHAR(120),
    cabinet_rc_ville       VARCHAR(120),
    accepte_le             TIMESTAMPTZ   NOT NULL,
    accepte_le_casablanca  VARCHAR(19)   NOT NULL,
    adresse_ip             VARCHAR(64)   NOT NULL,
    chaine_ip              VARCHAR(500),
    enregistre_le          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (conditions_id, version_id)
);
CREATE INDEX IF NOT EXISTS idx_acceptations_workspace
    ON contrat_acceptations (workspace_id, accepte_le DESC);

CREATE TABLE IF NOT EXISTS contrat_resiliations (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id      UUID          NOT NULL,
    conditions_id     UUID          NOT NULL UNIQUE REFERENCES conditions_particulieres(id),
    demandee_par      UUID          NOT NULL,
    demandeur_role    VARCHAR(20)   NOT NULL CHECK (demandeur_role IN ('SUPERVISEUR', 'SUPER_ADMIN')),
    date_effet        DATE          NOT NULL,
    motif             TEXT          NOT NULL CHECK (length(btrim(motif)) > 0),
    enregistree_le    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_resiliations_workspace ON contrat_resiliations (workspace_id);

CREATE TABLE IF NOT EXISTS contrat_renouvellements (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id        UUID          NOT NULL,
    conditions_id       UUID          NOT NULL REFERENCES conditions_particulieres(id),
    ancienne_echeance   DATE          NOT NULL,
    nouvelle_echeance   DATE          NOT NULL CHECK (nouvelle_echeance > ancienne_echeance),
    renouvele_le        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (conditions_id, ancienne_echeance)
);
CREATE INDEX IF NOT EXISTS idx_renouvellements_workspace ON contrat_renouvellements (workspace_id);

-- Journal des avis envoyes (copie du contrat, echeance a 30 jours, nouvelle
-- version) : un envoi rate reste visible (statut ECHEC, erreur), jamais avale.
CREATE TABLE IF NOT EXISTS contrat_avis (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id      UUID          NOT NULL,
    type              VARCHAR(30)   NOT NULL CHECK (type IN ('COPIE_CONTRAT', 'ECHEANCE_30_JOURS', 'NOUVELLE_VERSION')),
    cle               VARCHAR(120)  NOT NULL,
    destinataire      VARCHAR(200)  NOT NULL,
    statut            VARCHAR(10)   NOT NULL CHECK (statut IN ('ENVOYE', 'ECHEC')),
    erreur            TEXT,
    envoye_le         TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_avis_cle ON contrat_avis (type, cle, destinataire);
CREATE INDEX IF NOT EXISTS idx_avis_workspace ON contrat_avis (workspace_id, envoye_le DESC);

-- ---------------------------------------------------------------------
-- Inalterabilite.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION contrat_refuser_modification()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Historique du contrat inalterable (RG-CTR-02, RG-CTR-09) : % refuse sur %',
        TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'insufficient_privilege';
END
$$;

DROP TRIGGER IF EXISTS trg_contrat_versions_inalterable ON contrat_versions;
CREATE TRIGGER trg_contrat_versions_inalterable
    BEFORE UPDATE OR DELETE ON contrat_versions
    FOR EACH ROW EXECUTE FUNCTION contrat_refuser_modification();

DROP TRIGGER IF EXISTS trg_contrat_acceptations_inalterable ON contrat_acceptations;
CREATE TRIGGER trg_contrat_acceptations_inalterable
    BEFORE UPDATE OR DELETE ON contrat_acceptations
    FOR EACH ROW EXECUTE FUNCTION contrat_refuser_modification();

DROP TRIGGER IF EXISTS trg_contrat_resiliations_inalterable ON contrat_resiliations;
CREATE TRIGGER trg_contrat_resiliations_inalterable
    BEFORE UPDATE OR DELETE ON contrat_resiliations
    FOR EACH ROW EXECUTE FUNCTION contrat_refuser_modification();

DROP TRIGGER IF EXISTS trg_contrat_renouvellements_inalterable ON contrat_renouvellements;
CREATE TRIGGER trg_contrat_renouvellements_inalterable
    BEFORE UPDATE OR DELETE ON contrat_renouvellements
    FOR EACH ROW EXECUTE FUNCTION contrat_refuser_modification();

DROP TRIGGER IF EXISTS trg_contrat_avis_inalterable ON contrat_avis;
CREATE TRIGGER trg_contrat_avis_inalterable
    BEFORE UPDATE OR DELETE ON contrat_avis
    FOR EACH ROW EXECUTE FUNCTION contrat_refuser_modification();

-- TRUNCATE ne declenche pas les declencheurs de ligne : declencheur d'instruction.
DROP TRIGGER IF EXISTS trg_contrat_acceptations_sans_truncate ON contrat_acceptations;
CREATE TRIGGER trg_contrat_acceptations_sans_truncate
    BEFORE TRUNCATE ON contrat_acceptations
    FOR EACH STATEMENT EXECUTE FUNCTION contrat_refuser_modification();

-- Conditions particulieres : termes figes, suppression refusee.
CREATE OR REPLACE FUNCTION conditions_particulieres_termes_figes()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Conditions particulieres conservees (RG-CTR-09) : suppression refusee'
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.workspace_id IS DISTINCT FROM OLD.workspace_id
       OR NEW.reference IS DISTINCT FROM OLD.reference
       OR NEW.forfait IS DISTINCT FROM OLD.forfait
       OR NEW.rythme_paiement IS DISTINCT FROM OLD.rythme_paiement
       OR NEW.prix_ht IS DISTINCT FROM OLD.prix_ht
       OR NEW.date_debut IS DISTINCT FROM OLD.date_debut
       OR NEW.date_fin IS DISTINCT FROM OLD.date_fin
       OR NEW.mode_paiement IS DISTINCT FROM OLD.mode_paiement
       OR NEW.origine IS DISTINCT FROM OLD.origine
       OR NEW.cree_par IS DISTINCT FROM OLD.cree_par
       OR NEW.cree_le IS DISTINCT FROM OLD.cree_le THEN
        RAISE EXCEPTION 'Termes des conditions particulieres figes (RG-CTR-02) : seuls l''etat et l''echeance changent'
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    IF OLD.statut IN ('REMPLACE', 'ANNULE', 'RESILIE') AND NEW.statut IS DISTINCT FROM OLD.statut THEN
        RAISE EXCEPTION 'Conditions particulieres closes (%) : etat definitif', OLD.statut
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS trg_conditions_particulieres_termes_figes ON conditions_particulieres;
CREATE TRIGGER trg_conditions_particulieres_termes_figes
    BEFORE UPDATE OR DELETE ON conditions_particulieres
    FOR EACH ROW EXECUTE FUNCTION conditions_particulieres_termes_figes();

-- ---------------------------------------------------------------------
-- RLS (donnees de cabinet).
-- ---------------------------------------------------------------------
ALTER TABLE conditions_particulieres ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS conditions_particulieres_isolation ON conditions_particulieres;
CREATE POLICY conditions_particulieres_isolation ON conditions_particulieres FOR ALL
    USING (current_setting('app.contrat_portee', true) = 'globale'
           OR workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid)
    WITH CHECK (current_setting('app.contrat_portee', true) = 'globale'
           OR workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid);

ALTER TABLE contrat_acceptations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS contrat_acceptations_isolation ON contrat_acceptations;
CREATE POLICY contrat_acceptations_isolation ON contrat_acceptations FOR ALL
    USING (current_setting('app.contrat_portee', true) = 'globale'
           OR workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid)
    WITH CHECK (current_setting('app.contrat_portee', true) = 'globale'
           OR workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid);

ALTER TABLE contrat_resiliations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS contrat_resiliations_isolation ON contrat_resiliations;
CREATE POLICY contrat_resiliations_isolation ON contrat_resiliations FOR ALL
    USING (current_setting('app.contrat_portee', true) = 'globale'
           OR workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid)
    WITH CHECK (current_setting('app.contrat_portee', true) = 'globale'
           OR workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid);

ALTER TABLE contrat_renouvellements ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS contrat_renouvellements_isolation ON contrat_renouvellements;
CREATE POLICY contrat_renouvellements_isolation ON contrat_renouvellements FOR ALL
    USING (current_setting('app.contrat_portee', true) = 'globale'
           OR workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid)
    WITH CHECK (current_setting('app.contrat_portee', true) = 'globale'
           OR workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid);

ALTER TABLE contrat_avis ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS contrat_avis_isolation ON contrat_avis;
CREATE POLICY contrat_avis_isolation ON contrat_avis FOR ALL
    USING (current_setting('app.contrat_portee', true) = 'globale'
           OR workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid)
    WITH CHECK (current_setting('app.contrat_portee', true) = 'globale'
           OR workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid);

-- ---------------------------------------------------------------------
-- Droits du role applicatif (au plus juste ; aucun DELETE, aucun TRUNCATE).
-- ---------------------------------------------------------------------
REVOKE ALL ON contrat_versions, conditions_particulieres, contrat_acceptations,
    contrat_resiliations, contrat_renouvellements, contrat_avis FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        REVOKE ALL ON contrat_versions, conditions_particulieres, contrat_acceptations,
            contrat_resiliations, contrat_renouvellements, contrat_avis FROM jurika_app;
        GRANT SELECT, INSERT ON contrat_versions, contrat_acceptations, contrat_resiliations,
            contrat_renouvellements, contrat_avis TO jurika_app;
        GRANT SELECT, INSERT, UPDATE ON conditions_particulieres TO jurika_app;
        GRANT USAGE, SELECT ON SEQUENCE conditions_particulieres_numero TO jurika_app;
    END IF;
END
$$;
