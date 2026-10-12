-- =====================================================================
-- JURIKA V37 -- Lot L4d (E3) : une nouvelle version du corpus ne fait pas
-- perdre l'avancement des tickets en cours.
--
-- Les etats d'un ticket (ticket_demarches) et les delais saisis sur la ligne
-- (ticket_demarche_delais) designent une ligne d'UNE version du referentiel.
-- A l'import d'une nouvelle version, chaque etat suit la ligne de la nouvelle
-- version qui occupe la meme place : meme matrice, meme numero de ligne, meme
-- decision. Un etat sans ligne correspondante reste sur l'ancienne ligne
-- (rien n'est supprime) et il est COMPTE : le rapport d'import le dit.
--
-- L'import traverse tous les workspaces : jurika_app est soumis a la RLS, d'ou
-- une fonction SECURITY DEFINER (regles de auth V34 : usage unique, aucun SQL
-- dynamique, search_path fixe, EXECUTE au seul jurika_app). L'autorisation
-- (SUPER_ADMIN, ou import initial au demarrage) est verifiee par le serveur.
-- =====================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        RAISE EXCEPTION 'Role jurika_app absent : le creer avant le demarrage des services';
    END IF;
END
$$;

CREATE OR REPLACE FUNCTION ticket_reporter_etats_demarches(p_version text,
                                                           OUT reportes integer,
                                                           OUT non_reportes integer)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    n integer;
BEGIN
    UPDATE ticket_demarches td SET demarche_id = n2.id, updated_at = now()
    FROM demarches_referentiel o, demarches_referentiel n2
    WHERE td.demarche_id = o.id
      AND o.corpus_version IS NOT NULL AND o.corpus_version <> p_version
      AND n2.corpus_version = p_version AND n2.actif
      AND n2.matrice = o.matrice AND n2.ordre = o.ordre
      AND n2.decision_code IS NOT DISTINCT FROM o.decision_code;
    GET DIAGNOSTICS reportes = ROW_COUNT;

    UPDATE ticket_demarche_delais dl SET demarche_id = n2.id
    FROM demarches_referentiel o, demarches_referentiel n2
    WHERE dl.demarche_id = o.id
      AND o.corpus_version IS NOT NULL AND o.corpus_version <> p_version
      AND n2.corpus_version = p_version AND n2.actif
      AND n2.matrice = o.matrice AND n2.ordre = o.ordre
      AND n2.decision_code IS NOT DISTINCT FROM o.decision_code;

    SELECT count(*) INTO n
    FROM ticket_demarches td JOIN demarches_referentiel o ON o.id = td.demarche_id
    WHERE o.corpus_version IS NOT NULL AND o.corpus_version <> p_version;
    non_reportes := n;
END
$$;

REVOKE ALL ON FUNCTION ticket_reporter_etats_demarches(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION ticket_reporter_etats_demarches(text) TO jurika_app;
