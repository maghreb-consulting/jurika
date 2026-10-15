-- =====================================================================
-- JURIKA V41 (auth-service) -- Lot L7 (E4), RG-ESS-10 : suppression
-- definitive d'un essai non souscrit, au terme des 30 jours de lecture seule
-- et apres avertissement.
--
-- Une seule fonction SECURITY DEFINER, appelee par le cycle planifie de
-- l'essai. Garde-fous VERIFIES DANS LA FONCTION (en plus du serveur) :
--   * le workspace est un essai ouvert par l'inscription a l'essai (ligne
--     dans essais_registre), jamais un cabinet cree autrement ;
--   * trial_status = 'TRIAL_EXPIRED' (lecture seule), jamais CONVERTED ;
--   * aucune trace d'abonnement (subscription_status NULL) : jamais un
--     cabinet abonne, meme resilie (RG-TAR-07 : archive, jamais supprime) ;
--   * avertissement envoye (essai_avertissement_le renseigne) ;
--   * lecture seule commencee depuis 30 jours au moins (essai_expire_le).
-- Sinon : exception, rien n'est supprime.
--
-- Suppression : TOUTES les lignes du cabinet dans jurika_db, c'est-a-dire
-- chaque table du schema public qui porte une colonne workspace_id (tables
-- de tous les services, presentes et futures, dont chatbot_conversations et
-- chatbot_echanges), puis la ligne de workspaces. Seul essais_registre est
-- conserve (un essai par cabinet, RG-ESS-02 : empreintes seulement) et
-- marque supprime. Les cles etrangeres sans ON DELETE CASCADE imposent un
-- ordre : la fonction repasse sur les tables restantes tant qu'elle
-- progresse ; si une table ne peut pas etre videe, exception et retour
-- arriere complet (une seule transaction).
--
-- Ecart assume aux regles de V34 (aucun SQL dynamique) : la liste des tables
-- est lue dans le catalogue (pg_class / pg_attribute) pour n'en oublier
-- aucune ; les identifiants passent par format('%I') et la valeur par USING,
-- rien ne vient de l'appelant hormis l'identifiant du workspace.
--
-- Le reglage app.current_workspace_id est pose sur le workspace supprime :
-- les tables en FORCE ROW LEVEL SECURITY (audit_log) laissent alors leur
-- proprietaire supprimer les lignes de ce workspace, et de lui seul.
-- =====================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        RAISE EXCEPTION 'Role jurika_app absent : le creer avant le demarrage des services '
            '(init-db.sh ou rattrapage-role-applicatif.sh, cf. ROLE_APPLICATIF_RDS.md)';
    END IF;
END
$$;

CREATE OR REPLACE FUNCTION essai_supprimer_definitivement(p_workspace uuid)
RETURNS TABLE (table_nom text, lignes bigint)
LANGUAGE plpgsql VOLATILE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_statut_essai   varchar;
    v_abonnement     varchar;
    v_expire_le      timestamptz;
    v_averti_le      timestamptz;
    v_restantes      text[];
    v_suivantes      text[];
    v_table          text;
    v_n              bigint;
BEGIN
    IF p_workspace IS NULL THEN
        RAISE EXCEPTION 'essai_supprimer_definitivement : workspace absent';
    END IF;
    PERFORM set_config('app.current_workspace_id', p_workspace::text, true);

    SELECT w.trial_status, w.subscription_status, w.essai_expire_le, w.essai_avertissement_le
      INTO v_statut_essai, v_abonnement, v_expire_le, v_averti_le
      FROM workspaces w WHERE w.id = p_workspace
      FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'essai_supprimer_definitivement : workspace % inconnu', p_workspace;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM essais_registre r
                    WHERE r.workspace_id = p_workspace AND r.supprime_le IS NULL) THEN
        RAISE EXCEPTION 'essai_supprimer_definitivement : % n''est pas un essai gratuit', p_workspace;
    END IF;
    IF v_statut_essai IS DISTINCT FROM 'TRIAL_EXPIRED' THEN
        RAISE EXCEPTION 'essai_supprimer_definitivement : % n''est pas un essai en lecture seule (statut %)',
            p_workspace, v_statut_essai;
    END IF;
    IF v_abonnement IS NOT NULL THEN
        RAISE EXCEPTION 'essai_supprimer_definitivement : % porte un abonnement (%), jamais supprime',
            p_workspace, v_abonnement;
    END IF;
    IF v_averti_le IS NULL THEN
        RAISE EXCEPTION 'essai_supprimer_definitivement : % n''a pas ete averti', p_workspace;
    END IF;
    IF v_expire_le IS NULL OR v_expire_le > now() - make_interval(days => 30) THEN
        RAISE EXCEPTION 'essai_supprimer_definitivement : lecture seule de % commencee depuis moins de 30 jours',
            p_workspace;
    END IF;

    SELECT array_agg(c.relname::text ORDER BY c.relname)
      INTO v_restantes
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
      JOIN pg_attribute a ON a.attrelid = c.oid
     WHERE n.nspname = 'public'
       AND c.relkind IN ('r', 'p')
       AND a.attname = 'workspace_id'
       AND NOT a.attisdropped
       AND c.relname NOT IN ('essais_registre', 'workspaces');

    WHILE v_restantes IS NOT NULL AND array_length(v_restantes, 1) > 0 LOOP
        v_suivantes := ARRAY[]::text[];
        FOREACH v_table IN ARRAY v_restantes LOOP
            BEGIN
                EXECUTE format('DELETE FROM public.%I WHERE workspace_id = $1', v_table) USING p_workspace;
                GET DIAGNOSTICS v_n = ROW_COUNT;
                table_nom := v_table;
                lignes := v_n;
                RETURN NEXT;
            EXCEPTION WHEN foreign_key_violation THEN
                v_suivantes := v_suivantes || v_table;
            END;
        END LOOP;
        IF array_length(v_suivantes, 1) IS NOT NULL
           AND array_length(v_suivantes, 1) = array_length(v_restantes, 1) THEN
            RAISE EXCEPTION 'essai_supprimer_definitivement : tables impossibles a vider pour % : %',
                p_workspace, array_to_string(v_suivantes, ', ');
        END IF;
        v_restantes := v_suivantes;
    END LOOP;

    DELETE FROM workspaces WHERE id = p_workspace;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    table_nom := 'workspaces';
    lignes := v_n;
    RETURN NEXT;

    UPDATE essais_registre SET supprime_le = now() WHERE workspace_id = p_workspace;
END
$$;

REVOKE EXECUTE ON FUNCTION essai_supprimer_definitivement(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION essai_supprimer_definitivement(uuid) TO jurika_app;
