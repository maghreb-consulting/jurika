-- =====================================================================
-- JURIKA billing V16 -- Lot L7, etape E9 : version 1 du contrat d'abonnement
-- et reponses aux nouvelles versions des conditions generales (RG-CTR-07,
-- article 16 des conditions generales).
--
-- 1. Versions retirees. Le projet du 12 octobre 2026 (code "2026-10-12",
--    origine INITIALE, mentions entre crochets) a ete charge au demarrage sur
--    une base vivante avant la validation du texte. contrat_versions est
--    inalterable (V13) : la ligne reste, elle est marquee ici RETIREE, ce qui
--    l'exclut de la version en vigueur, de la version a venir et des versions
--    servies aux cabinets. Elle n'est retiree que si aucun cabinet ne l'a
--    acceptee (sinon elle reste applicable a ce cabinet, RG-CTR-07). La
--    version 1 validee est chargee par l'application au demarrage.
--    Une base vierge ne porte pas cette ligne : rien n'est insere.
--
-- 2. Reponses aux nouvelles versions : "Accepter" ou "Refuser". Une reponse par
--    contrat (conditions particulieres) et par version ; ecrite une fois,
--    jamais modifiee ni supprimee (meme regime que contrat_acceptations :
--    declencheurs, SELECT et INSERT seuls pour jurika_app). Une acceptation
--    renvoie a la preuve de contrat_acceptations ; un refus vaut resiliation
--    a l'echeance (contrat_resiliations, meme contrat).
-- =====================================================================

CREATE TABLE IF NOT EXISTS contrat_versions_retraits (
    version_id   UUID          PRIMARY KEY REFERENCES contrat_versions(id),
    motif        TEXT          NOT NULL CHECK (length(btrim(motif)) > 0),
    retiree_le   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

INSERT INTO contrat_versions_retraits (version_id, motif)
SELECT v.id, 'Projet du 12 octobre 2026 charge avant la validation du texte par la direction : '
          || 'jamais presente a un cabinet, remplace par la version 1 validee.'
FROM contrat_versions v
WHERE v.code = '2026-10-12'
  AND v.origine = 'INITIALE'
  AND NOT EXISTS (SELECT 1 FROM contrat_acceptations a WHERE a.version_id = v.id)
ON CONFLICT (version_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS contrat_reponses_versions (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id           UUID          NOT NULL,
    conditions_id          UUID          NOT NULL REFERENCES conditions_particulieres(id),
    version_id             UUID          NOT NULL REFERENCES contrat_versions(id),
    version_code           VARCHAR(40)   NOT NULL,
    reponse                VARCHAR(10)   NOT NULL CHECK (reponse IN ('ACCEPTEE', 'REFUSEE')),
    acceptation_id         UUID          REFERENCES contrat_acceptations(id),
    signataire_user_id     UUID          NOT NULL,
    signataire_compte      VARCHAR(200)  NOT NULL,
    signataire_role        VARCHAR(20)   NOT NULL CHECK (signataire_role = 'SUPERVISEUR'),
    repondu_le             TIMESTAMPTZ   NOT NULL,
    repondu_le_casablanca  VARCHAR(19)   NOT NULL,
    adresse_ip             VARCHAR(64)   NOT NULL,
    chaine_ip              VARCHAR(500),
    enregistre_le          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK ((reponse = 'ACCEPTEE') = (acceptation_id IS NOT NULL)),
    UNIQUE (conditions_id, version_id)
);
CREATE INDEX IF NOT EXISTS idx_reponses_versions_workspace
    ON contrat_reponses_versions (workspace_id, repondu_le DESC);

-- Inalterabilite (fonction de V13).
DROP TRIGGER IF EXISTS trg_contrat_versions_retraits_inalterable ON contrat_versions_retraits;
CREATE TRIGGER trg_contrat_versions_retraits_inalterable
    BEFORE UPDATE OR DELETE ON contrat_versions_retraits
    FOR EACH ROW EXECUTE FUNCTION contrat_refuser_modification();

DROP TRIGGER IF EXISTS trg_contrat_reponses_versions_inalterable ON contrat_reponses_versions;
CREATE TRIGGER trg_contrat_reponses_versions_inalterable
    BEFORE UPDATE OR DELETE ON contrat_reponses_versions
    FOR EACH ROW EXECUTE FUNCTION contrat_refuser_modification();

DROP TRIGGER IF EXISTS trg_contrat_reponses_versions_sans_truncate ON contrat_reponses_versions;
CREATE TRIGGER trg_contrat_reponses_versions_sans_truncate
    BEFORE TRUNCATE ON contrat_reponses_versions
    FOR EACH STATEMENT EXECUTE FUNCTION contrat_refuser_modification();

DROP TRIGGER IF EXISTS trg_contrat_versions_retraits_sans_truncate ON contrat_versions_retraits;
CREATE TRIGGER trg_contrat_versions_retraits_sans_truncate
    BEFORE TRUNCATE ON contrat_versions_retraits
    FOR EACH STATEMENT EXECUTE FUNCTION contrat_refuser_modification();

-- RLS (donnees de cabinet) : meme politique que les autres tables du contrat.
ALTER TABLE contrat_reponses_versions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS contrat_reponses_versions_isolation ON contrat_reponses_versions;
CREATE POLICY contrat_reponses_versions_isolation ON contrat_reponses_versions FOR ALL
    USING (current_setting('app.contrat_portee', true) = 'globale'
           OR workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid)
    WITH CHECK (current_setting('app.contrat_portee', true) = 'globale'
           OR workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid);

-- Droits du role applicatif : lecture seule des retraits (poses par migration),
-- SELECT et INSERT des reponses ; ni UPDATE, ni DELETE, ni TRUNCATE.
REVOKE ALL ON contrat_versions_retraits, contrat_reponses_versions FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        REVOKE ALL ON contrat_versions_retraits, contrat_reponses_versions FROM jurika_app;
        GRANT SELECT ON contrat_versions_retraits TO jurika_app;
        GRANT SELECT, INSERT ON contrat_reponses_versions TO jurika_app;
    END IF;
END
$$;
