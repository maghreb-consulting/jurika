-- =====================================================================
-- JURIKA billing V7 -- Lot L7 (E5) : forfaits du CDC 4.3, abonnement d'un an
-- (mensuel ou annuel), paiement par carte via le CMI, Stripe retire.
--
-- 1. Forfaits : essentiel, professionnel, max, sur_devis. Reprise des anciens
--    codes (symetrique d'auth V44) : business -> professionnel,
--    entreprise -> sur_devis.
-- 2. Stripe retire du code. Ses colonnes sont CONSERVEES, sans perte, comme
--    historique (plus jamais ecrites) : subscriptions.stripe_customer_id et
--    stripe_subscription_id (NOT NULL leve), payments.stripe_payment_id et
--    stripe_checkout_url ; les tables payment_methods, webhook_events et
--    invoices (miroir des factures Stripe) restent en place, sans ecriture.
-- 3. subscriptions : abonnement souscrit pour un an (RG-TAR-01, RG-TAR-02) :
--    rythme, engagement (debut, fin), periode payee (debut, fin exclue),
--    changement a la prochaine echeance (RG-TAR-05, RG-TAR-06), prix de
--    l'echeance d'un devis (Sur Devis, RG-PLN-05). L'ancienne contrainte
--    UNIQUE (workspace_id, status) interdisait deux abonnements termines pour un
--    meme cabinet (arret puis reprise, RG-TAR-08) : remplacee par l'unicite de
--    l'abonnement ACTIF.
-- 4. payments : objet (souscription, echeance, changement de forfait),
--    periode couverte, montants HT / TVA / TTC au taux du jour, paiement par
--    carte (oid CMI unique, code retour, identifiant de transaction),
--    enregistrement par le super-admin (date de reception, auteur),
--    propagation de l'activation vers auth-service (rejouee si elle echoue).
-- 5. cmi_retours : journal des retours serveur du CMI (RG-PAY-05) : signature
--    verifiee ou non, reponse rendue. Aucune donnee de carte n'y est ecrite
--    (RG-PAY-04) : seuls des champs nommes sont conserves.
--
-- Base dediee jurika_billing, connexion en proprietaire (dette L0 : bascule
-- jurika_app restante) : le filtrage par workspace est APPLICATIF et explicite
-- dans chaque requete d'un cabinet ; voir V8 pour la RLS des factures.
-- =====================================================================

-- 1. Forfaits ------------------------------------------------------------
ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS ck_plan_code_canonical;
UPDATE subscriptions SET plan_code = 'professionnel' WHERE plan_code = 'business';
UPDATE subscriptions SET plan_code = 'sur_devis'     WHERE plan_code = 'entreprise';
ALTER TABLE subscriptions ADD CONSTRAINT ck_plan_code_canonical
    CHECK (plan_code IN ('essentiel', 'professionnel', 'max', 'sur_devis'));

ALTER TABLE payments DROP CONSTRAINT IF EXISTS ck_payment_plan;
UPDATE payments SET plan_code = 'professionnel' WHERE plan_code = 'business';
UPDATE payments SET plan_code = 'sur_devis'     WHERE plan_code = 'entreprise';
ALTER TABLE payments ADD CONSTRAINT ck_payment_plan
    CHECK (plan_code IN ('essentiel', 'professionnel', 'max', 'sur_devis'));

-- 2. Historique Stripe ---------------------------------------------------
ALTER TABLE subscriptions ALTER COLUMN stripe_customer_id DROP NOT NULL;
ALTER TABLE subscriptions ALTER COLUMN stripe_subscription_id DROP NOT NULL;
COMMENT ON COLUMN subscriptions.stripe_customer_id IS 'Historique Stripe (avant L7), plus ecrit.';
COMMENT ON COLUMN subscriptions.stripe_subscription_id IS 'Historique Stripe (avant L7), plus ecrit.';
COMMENT ON COLUMN payments.stripe_payment_id IS 'Historique Stripe (avant L7), plus ecrit.';
COMMENT ON COLUMN payments.stripe_checkout_url IS 'Historique Stripe (avant L7), plus ecrit.';
COMMENT ON TABLE payment_methods IS 'Historique Stripe (avant L7), plus ecrit.';
COMMENT ON TABLE webhook_events IS 'Historique Stripe (avant L7), plus ecrit.';
COMMENT ON TABLE invoices IS 'Historique Stripe (avant L7), plus ecrit ; les factures JURIKA sont dans factures (V8).';

-- 3. Abonnement ----------------------------------------------------------
ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS uq_workspace_status;
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_active_par_workspace
    ON subscriptions (workspace_id) WHERE status = 'active';

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS rythme               VARCHAR(10),
    ADD COLUMN IF NOT EXISTS engagement_debut     DATE,
    ADD COLUMN IF NOT EXISTS engagement_fin       DATE,
    ADD COLUMN IF NOT EXISTS periode_debut        DATE,
    ADD COLUMN IF NOT EXISTS periode_fin          DATE,
    ADD COLUMN IF NOT EXISTS plan_a_venir         VARCHAR(50),
    ADD COLUMN IF NOT EXISTS rythme_a_venir       VARCHAR(10),
    ADD COLUMN IF NOT EXISTS prix_echeance_ht_centimes BIGINT,
    ADD COLUMN IF NOT EXISTS archive_motif        VARCHAR(20);

ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS ck_subscription_rythme;
ALTER TABLE subscriptions ADD CONSTRAINT ck_subscription_rythme
    CHECK (rythme IS NULL OR rythme IN ('monthly', 'yearly'));
ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS ck_subscription_rythme_a_venir;
ALTER TABLE subscriptions ADD CONSTRAINT ck_subscription_rythme_a_venir
    CHECK (rythme_a_venir IS NULL OR rythme_a_venir IN ('monthly', 'yearly'));
ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS ck_subscription_plan_a_venir;
ALTER TABLE subscriptions ADD CONSTRAINT ck_subscription_plan_a_venir
    CHECK (plan_a_venir IS NULL OR plan_a_venir IN ('essentiel', 'professionnel', 'max', 'sur_devis'));
ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS ck_subscription_archive_motif;
ALTER TABLE subscriptions ADD CONSTRAINT ck_subscription_archive_motif
    CHECK (archive_motif IS NULL OR archive_motif IN ('IMPAYE', 'FIN_ABONNEMENT', 'RESILIATION', 'DECISION'));

-- Reprise : periode payee = bornes deja connues (dates de Casablanca).
UPDATE subscriptions
   SET periode_debut = (current_period_start AT TIME ZONE 'UTC' AT TIME ZONE 'Africa/Casablanca')::date,
       periode_fin   = (current_period_end   AT TIME ZONE 'UTC' AT TIME ZONE 'Africa/Casablanca')::date
 WHERE periode_debut IS NULL;

COMMENT ON COLUMN subscriptions.rythme IS 'Lot L7 -- rythme de paiement : monthly (12 mensualites) | yearly (une fois par an). NULL = abonnement anterieur au lot.';
COMMENT ON COLUMN subscriptions.engagement_fin IS 'Lot L7 -- fin de l''annee d''abonnement (date anniversaire, exclue).';
COMMENT ON COLUMN subscriptions.periode_fin IS 'Lot L7 -- fin (exclue) de la periode payee ; au-dela sans paiement : archivage (RG-TAR-03, RG-PLN-07).';
COMMENT ON COLUMN subscriptions.plan_a_venir IS 'Lot L7 -- forfait inferieur demande, applique a la prochaine echeance (RG-TAR-05).';
COMMENT ON COLUMN subscriptions.rythme_a_venir IS 'Lot L7 -- rythme demande, applique a la prochaine echeance (RG-TAR-06).';
COMMENT ON COLUMN subscriptions.prix_echeance_ht_centimes IS 'Lot L7 -- Sur Devis : prix HT de l''echeance convenu par devis (centimes MAD).';

-- 4. Paiements -----------------------------------------------------------
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS objet                VARCHAR(20),
    ADD COLUMN IF NOT EXISTS plan_precedent       VARCHAR(50),
    ADD COLUMN IF NOT EXISTS periode_debut        DATE,
    ADD COLUMN IF NOT EXISTS periode_fin          DATE,
    ADD COLUMN IF NOT EXISTS montant_ht_centimes  BIGINT,
    ADD COLUMN IF NOT EXISTS montant_tva_centimes BIGINT,
    ADD COLUMN IF NOT EXISTS taux_tva             NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS cmi_oid              VARCHAR(64),
    ADD COLUMN IF NOT EXISTS cmi_code_retour      VARCHAR(10),
    ADD COLUMN IF NOT EXISTS cmi_transaction      VARCHAR(64),
    ADD COLUMN IF NOT EXISTS recu_le              DATE,
    ADD COLUMN IF NOT EXISTS enregistre_par       UUID,
    ADD COLUMN IF NOT EXISTS activation_propagee  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS inscription_en_ligne BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS facture_refus        TEXT;

ALTER TABLE payments DROP CONSTRAINT IF EXISTS ck_payment_objet;
ALTER TABLE payments ADD CONSTRAINT ck_payment_objet
    CHECK (objet IS NULL OR objet IN ('SOUSCRIPTION', 'ECHEANCE', 'CHANGEMENT_FORFAIT'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_cmi_oid ON payments (cmi_oid) WHERE cmi_oid IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_payments_a_propager ON payments (status)
    WHERE status = 'COMPLETED' AND activation_propagee = FALSE;

-- Les paiements deja valides avant le lot ont ete propages par l'ancien chemin.
UPDATE payments SET activation_propagee = TRUE WHERE status = 'COMPLETED';

COMMENT ON COLUMN payments.objet IS 'Lot L7 -- SOUSCRIPTION (premiere ou reprise) | ECHEANCE (renouvellement) | CHANGEMENT_FORFAIT (difference au prorata, RG-TAR-04).';
COMMENT ON COLUMN payments.amount_mad_cents IS 'Montant a regler TTC, centimes MAD (Lot L7 : HT + TVA au taux de l''editeur).';
COMMENT ON COLUMN payments.cmi_oid IS 'Lot L7 -- numero de commande transmis au CMI (oid), unique : une confirmation recue deux fois ne produit qu''un paiement (RG-PAY-05).';
COMMENT ON COLUMN payments.recu_le IS 'Lot L7 -- virement, cheque, especes : date de reception saisie par le super-admin (RG-PAY-02).';
COMMENT ON COLUMN payments.activation_propagee IS 'Lot L7 -- vrai quand auth-service a recu l''activation ; sinon rejouee (BillingTachesPlanifiees).';
COMMENT ON COLUMN payments.inscription_en_ligne IS 'Lot L7 -- paiement de l''inscription en ligne : les identifiants du superviseur sont emis a l''activation (premiere souscription seulement).';
COMMENT ON COLUMN payments.facture_refus IS 'Lot L7 -- facture non emise : mentions obligatoires manquantes, nommees (RG-PAY-03).';

-- 5. Retours du CMI ------------------------------------------------------
CREATE TABLE IF NOT EXISTS cmi_retours (
    id                BIGSERIAL    PRIMARY KEY,
    recu_le           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    oid               VARCHAR(64),
    signature_valide  BOOLEAN      NOT NULL,
    code_retour       VARCHAR(10),
    reponse_cmi       VARCHAR(20),
    transaction       VARCHAR(64),
    montant           VARCHAR(20),
    reponse_rendue    VARCHAR(40)  NOT NULL,
    payment_id        BIGINT       REFERENCES payments(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_cmi_retours_oid ON cmi_retours (oid);
COMMENT ON TABLE cmi_retours IS 'Lot L7 -- journal des retours serveur du CMI (RG-PAY-05). Aucune donnee de carte (RG-PAY-04).';
