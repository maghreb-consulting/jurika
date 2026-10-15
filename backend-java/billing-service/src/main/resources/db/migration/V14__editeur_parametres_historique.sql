-- =====================================================================
-- JURIKA billing V14 -- Lot L7, etape E6 : trace des parametres de l'editeur.
--
-- Chaque modification de editeur_parametres (identite de Maghreb Consulting,
-- taux de TVA) laisse une ligne par champ change : auteur, date, ancienne et
-- nouvelle valeur (P7, tracabilite). La base jurika_billing n'a pas de table
-- audit_log : la trace vit ici.
--
-- Pour qu'aucun chemin d'ecriture n'echappe a la trace (motif 9), elle est
-- posee par un DECLENCHEUR sur la table et non par le service. L'auteur est la
-- colonne modifie_par de la ligne ecrite (renseignee par le service).
-- La fonction s'execute avec les droits de son proprietaire : le role
-- applicatif n'a besoin que de SELECT sur la trace, qu'il ne peut ni modifier
-- ni supprimer (declencheur et droits).
-- Le schema de editeur_parametres (V6) est inchange.
-- =====================================================================

CREATE TABLE IF NOT EXISTS editeur_parametres_historique (
    id               BIGSERIAL     PRIMARY KEY,
    champ            VARCHAR(40)   NOT NULL,
    ancienne_valeur  TEXT,
    nouvelle_valeur  TEXT,
    modifie_par      UUID,
    modifie_le       TIMESTAMPTZ   NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX IF NOT EXISTS idx_editeur_historique_date ON editeur_parametres_historique (modifie_le DESC);

CREATE OR REPLACE FUNCTION tracer_editeur_parametres()
RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_avant jsonb := to_jsonb(OLD);
    v_apres jsonb := to_jsonb(NEW);
    v_champ text;
BEGIN
    FOREACH v_champ IN ARRAY ARRAY[
            'raison_sociale', 'forme_juridique', 'capital_social', 'siege_social', 'ville',
            'rc_numero', 'rc_tribunal', 'ice', 'identifiant_fiscal', 'taxe_professionnelle',
            'representant_nom', 'representant_qualite', 'taux_tva_pourcent'] LOOP
        IF (v_avant ->> v_champ) IS DISTINCT FROM (v_apres ->> v_champ) THEN
            INSERT INTO editeur_parametres_historique (champ, ancienne_valeur, nouvelle_valeur, modifie_par)
            VALUES (v_champ, v_avant ->> v_champ, v_apres ->> v_champ, NEW.modifie_par);
        END IF;
    END LOOP;
    RETURN NULL;
END
$$;

REVOKE ALL ON FUNCTION tracer_editeur_parametres() FROM PUBLIC;

DROP TRIGGER IF EXISTS trg_tracer_editeur_parametres ON editeur_parametres;
CREATE TRIGGER trg_tracer_editeur_parametres
    AFTER UPDATE ON editeur_parametres
    FOR EACH ROW EXECUTE FUNCTION tracer_editeur_parametres();

CREATE OR REPLACE FUNCTION editeur_historique_inalterable()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Trace des parametres de l''editeur inalterable : % refuse', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END
$$;

DROP TRIGGER IF EXISTS trg_editeur_historique_inalterable ON editeur_parametres_historique;
CREATE TRIGGER trg_editeur_historique_inalterable
    BEFORE UPDATE OR DELETE ON editeur_parametres_historique
    FOR EACH ROW EXECUTE FUNCTION editeur_historique_inalterable();

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        REVOKE ALL ON editeur_parametres_historique FROM jurika_app;
        GRANT SELECT ON editeur_parametres_historique TO jurika_app;
    END IF;
END
$$;
