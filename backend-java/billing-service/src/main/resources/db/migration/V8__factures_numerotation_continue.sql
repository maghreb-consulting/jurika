-- =====================================================================
-- JURIKA billing V8 -- Lot L7 (E5), RG-PAY-03 : factures JURIKA.
--
-- Une facture par paiement acquis (contrainte UNIQUE sur payment_id : une
-- confirmation recue deux fois ne produit qu'une facture, RG-PAY-05),
-- numerotee SANS RUPTURE : compteur par annee (factures_sequence), incremente
-- dans la transaction qui cree la facture (le verrou de ligne serialise les
-- emissions concurrentes ; une transaction annulee annule aussi son numero).
-- Numero : F<annee>-<six chiffres> (ex. F2026-000001).
--
-- Les mentions obligatoires sont FIGEES a l'emission (identite de l'editeur et
-- du cabinet en JSONB, montants, taux) et le PDF est conserve tel qu'emis :
-- une facture ne se modifie jamais. Aucune facture ne sort si une mention
-- obligatoire de l'editeur ou du cabinet manque (refus nomme, payments.
-- facture_refus).
--
-- RLS : politique par workspace posee pour la bascule vers jurika_app ; tant
-- que billing se connecte en proprietaire (dette L0), elle n'est pas
-- appliquee et le filtrage APPLICATIF (workspace_id dans chaque requete du
-- cabinet) est le controle effectif. Droits jurika_app au plus juste :
-- lecture et insertion, ni modification ni suppression.
-- =====================================================================

CREATE TABLE IF NOT EXISTS factures_sequence (
    annee          INTEGER  PRIMARY KEY CHECK (annee BETWEEN 2000 AND 2999),
    dernier_numero INTEGER  NOT NULL CHECK (dernier_numero >= 0)
);

CREATE TABLE IF NOT EXISTS factures (
    id                    BIGSERIAL     PRIMARY KEY,
    numero                VARCHAR(20)   NOT NULL UNIQUE,
    annee                 INTEGER       NOT NULL,
    rang                  INTEGER       NOT NULL,
    workspace_id          UUID          NOT NULL,
    payment_id            BIGINT        NOT NULL UNIQUE REFERENCES payments(id),
    date_emission         DATE          NOT NULL,
    designation           VARCHAR(400)  NOT NULL,
    periode_debut         DATE,
    periode_fin           DATE,
    mode_paiement         VARCHAR(30)   NOT NULL,
    montant_ht_centimes   BIGINT        NOT NULL CHECK (montant_ht_centimes >= 0),
    taux_tva              NUMERIC(5,2)  NOT NULL,
    montant_tva_centimes  BIGINT        NOT NULL CHECK (montant_tva_centimes >= 0),
    montant_ttc_centimes  BIGINT        NOT NULL CHECK (montant_ttc_centimes = montant_ht_centimes + montant_tva_centimes),
    editeur               JSONB         NOT NULL,
    cabinet               JSONB         NOT NULL,
    pdf                   BYTEA         NOT NULL,
    cree_le               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_factures_annee_rang UNIQUE (annee, rang)
);

CREATE INDEX IF NOT EXISTS idx_factures_workspace ON factures (workspace_id, date_emission DESC);

COMMENT ON TABLE factures IS 'Lot L7 (RG-PAY-03) -- factures JURIKA, une par paiement acquis, numerotation sans rupture, mentions figees.';
COMMENT ON COLUMN factures.numero IS 'F<annee>-<rang sur six chiffres>, rang continu dans l''annee (factures_sequence).';

ALTER TABLE factures ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS factures_isolation ON factures;
CREATE POLICY factures_isolation ON factures FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        GRANT SELECT, INSERT ON factures TO jurika_app;
        REVOKE UPDATE, DELETE, TRUNCATE ON factures FROM jurika_app;
        GRANT USAGE ON SEQUENCE factures_id_seq TO jurika_app;
        GRANT SELECT, INSERT, UPDATE ON factures_sequence TO jurika_app;
        REVOKE DELETE, TRUNCATE ON factures_sequence FROM jurika_app;
    END IF;
END
$$;
