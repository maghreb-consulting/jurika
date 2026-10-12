-- =====================================================================
-- JURIKA V41 (ticket-service) -- Lot L5, etape E7 : trace et provenance des
-- identifiants de la societe (fiche client, RG-FIC-03 et RG-FIC-04).
--
-- RG-FIC-03 : chaque information de la fiche affiche sa provenance (identifiant
-- obtenu en fin de parcours, valeur lue sur une piece et confirmee, reprise a
-- l'import, saisie manuelle).
-- RG-FIC-04 : toute modification est tracee (auteur, date, ancienne et nouvelle
-- valeur).
--
-- Plusieurs services ecrivent entreprise_dossiers (ticket : fiche, cloture ;
-- workflow : creation, import, modification). Pour qu'aucun chemin d'ecriture
-- n'echappe a la trace (motif 9 : un controle silencieusement inoperant), la
-- trace est posee par un DECLENCHEUR sur la table, et non par chaque service.
--
-- Provenance : l'ecrivain la declare dans des reglages de transaction
-- (set_config(..., true)) :
--   app.identifiants_origine  SAISIE | FIN_PARCOURS | EXTRAITE
--   app.identifiants_auteur   utilisateur auteur (facultatif)
--   app.identifiants_ticket   ticket porteur (facultatif)
-- Sans declaration : OUVERTURE a l'insertion du dossier, PARCOURS a la mise a
-- jour (ecriture d'un parcours) ; le ticket est alors celui du dossier s'il est
-- le SEUL ouvert, sinon il reste vide (jamais devine).
--
-- Les valeurs anterieures a cette migration n'ont pas de trace : aucune ligne
-- n'est fabriquee, la fiche les affiche "provenance non tracee".
--
-- La fonction s'execute avec les droits de son proprietaire (le role qui
-- migre, non soumis a la RLS) : un service connecte en jurika_app sans
-- workspace courant ne voit pas son ecriture refusee par la trace.
-- =====================================================================

CREATE TABLE IF NOT EXISTS dossier_identifiants_historique (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    dossier_id      UUID         NOT NULL REFERENCES entreprise_dossiers(id) ON DELETE CASCADE,
    champ           VARCHAR(40)  NOT NULL CHECK (champ IN (
                        'raison_sociale', 'forme_juridique', 'capital_social_mad',
                        'adresse_siege', 'ville', 'rc_numero', 'rc_tribunal', 'ice',
                        'identifiant_fiscal', 'taxe_professionnelle', 'cnss',
                        'date_constitution')),
    ancienne_valeur TEXT,
    nouvelle_valeur TEXT,
    origine         VARCHAR(20)  NOT NULL CHECK (origine IN (
                        'SAISIE', 'FIN_PARCOURS', 'EXTRAITE', 'OUVERTURE', 'PARCOURS')),
    ticket_id       UUID         REFERENCES tickets(id) ON DELETE SET NULL,
    auteur_id       UUID         REFERENCES users(id) ON DELETE SET NULL,
    modifie_le      TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS idx_identifiants_historique_dossier
    ON dossier_identifiants_historique (workspace_id, dossier_id, champ, modifie_le);

ALTER TABLE dossier_identifiants_historique ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS identifiants_historique_isolation ON dossier_identifiants_historique;
CREATE POLICY identifiants_historique_isolation ON dossier_identifiants_historique FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid);

CREATE OR REPLACE FUNCTION tracer_identifiants_dossier()
RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_origine text := NULLIF(current_setting('app.identifiants_origine', true), '');
    v_auteur  uuid := NULLIF(current_setting('app.identifiants_auteur', true), '')::uuid;
    v_ticket  uuid := NULLIF(current_setting('app.identifiants_ticket', true), '')::uuid;
    v_avant   jsonb := '{}'::jsonb;
    v_apres   jsonb := to_jsonb(NEW);
    v_champ   text;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        v_avant := to_jsonb(OLD);
    END IF;
    IF v_origine IS NULL THEN
        IF TG_OP = 'INSERT' THEN
            v_origine := 'OUVERTURE';
        ELSE
            v_origine := 'PARCOURS';
            SELECT CASE WHEN count(*) = 1 THEN (min(t.id::text))::uuid END INTO v_ticket
              FROM tickets t
             WHERE t.workspace_id = NEW.workspace_id AND t.dossier_id = NEW.id
               AND t.statut NOT IN ('CLOTURE_DOSSIER', 'ANNULE', 'CLOTURE');
        END IF;
    END IF;
    FOREACH v_champ IN ARRAY ARRAY[
            'raison_sociale', 'forme_juridique', 'capital_social_mad', 'adresse_siege',
            'ville', 'rc_numero', 'rc_tribunal', 'ice', 'identifiant_fiscal',
            'taxe_professionnelle', 'cnss', 'date_constitution'] LOOP
        IF NULLIF(btrim(v_avant ->> v_champ), '') IS DISTINCT FROM NULLIF(btrim(v_apres ->> v_champ), '') THEN
            INSERT INTO dossier_identifiants_historique
                (workspace_id, dossier_id, champ, ancienne_valeur, nouvelle_valeur, origine, ticket_id, auteur_id)
            VALUES (NEW.workspace_id, NEW.id, v_champ, v_avant ->> v_champ, v_apres ->> v_champ,
                    v_origine, v_ticket, v_auteur);
        END IF;
    END LOOP;
    RETURN NULL;
END
$$;

REVOKE ALL ON FUNCTION tracer_identifiants_dossier() FROM PUBLIC;

DROP TRIGGER IF EXISTS trg_tracer_identifiants_dossier ON entreprise_dossiers;
CREATE TRIGGER trg_tracer_identifiants_dossier
    AFTER INSERT OR UPDATE OF raison_sociale, forme_juridique, capital_social_mad, adresse_siege,
        ville, rc_numero, rc_tribunal, ice, identifiant_fiscal, taxe_professionnelle, cnss,
        date_constitution
    ON entreprise_dossiers
    FOR EACH ROW EXECUTE FUNCTION tracer_identifiants_dossier();

-- Droits : ceux du role applicatif sur toute table (migrations *droits_role_applicatif).
-- Un REVOKE pose ici serait annule par le GRANT ... ON ALL TABLES d'une migration
-- d'un autre service jouee ensuite (workflow V14) : il ne garantirait rien, il n'est
-- donc pas pose. La trace n'est ecrite que par le declencheur ; aucun code ne la
-- modifie ni ne la supprime.
