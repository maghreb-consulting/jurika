-- =====================================================================
-- JURIKA billing V6 -- Lot L7 (socle commun) : parametres de l'editeur.
--
-- Identite de Maghreb Consulting (editeur de JURIKA), saisie une fois par le
-- super-admin, qui alimente les factures (RG-PAY-03) et le contrat
-- d'abonnement (paragraphe 4.8). Une seule ligne (id = 1), creee VIDE : aucune
-- valeur n'est inventee ; les documents qui en ont besoin refusent de sortir
-- tant qu'une mention obligatoire manque (donnee nommee).
-- Le taux de TVA est reglable par le super-admin ; 20.00 reprend la valeur
-- deja configuree jusqu'ici (BillingProperties.tvaRatePercent).
-- =====================================================================

CREATE TABLE IF NOT EXISTS editeur_parametres (
    id                        SMALLINT     PRIMARY KEY CHECK (id = 1),
    raison_sociale            VARCHAR(200),
    forme_juridique           VARCHAR(120),
    capital_social            VARCHAR(60),
    siege_social              VARCHAR(400),
    ville                     VARCHAR(120),
    rc_numero                 VARCHAR(60),
    rc_tribunal               VARCHAR(120),
    ice                       VARCHAR(20),
    identifiant_fiscal        VARCHAR(30),
    taxe_professionnelle      VARCHAR(30),
    representant_nom          VARCHAR(200),
    representant_qualite      VARCHAR(120),
    taux_tva_pourcent         NUMERIC(5,2) NOT NULL DEFAULT 20.00
                              CHECK (taux_tva_pourcent >= 0 AND taux_tva_pourcent <= 30),
    modifie_le                TIMESTAMPTZ,
    modifie_par               UUID
);

INSERT INTO editeur_parametres (id) VALUES (1) ON CONFLICT (id) DO NOTHING;
