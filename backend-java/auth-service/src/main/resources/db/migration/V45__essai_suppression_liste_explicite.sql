-- =====================================================================
-- JURIKA V45 (auth-service) -- Lot L7 (E10), RG-ESS-10 : suppression
-- definitive d'un essai par une LISTE EXPLICITE de tables.
--
-- Remplace la fonction de V41 (appliquee, intouchable), qui lisait dans le
-- catalogue les tables portant une colonne workspace_id et les vidait par SQL
-- dynamique, en reessayant tant que des cles etrangeres bloquaient (decision
-- du 2026-10-14). Desormais :
--   * chaque table est nommee, dans un ORDRE explicite qui respecte les cles
--     etrangeres (enfants d'abord, workspaces en dernier) ;
--   * aucun SQL dynamique, aucune boucle de reessai : une cle etrangere qui
--     bloque fait echouer la fonction, et la transaction est annulee ;
--   * les cinq garde-fous de V41 sont repris a l'identique (registre des
--     essais, lecture seule, aucune trace d'abonnement, avertissement envoye,
--     30 jours), ainsi que les droits (SECURITY DEFINER, search_path fige,
--     EXECUTE retire a PUBLIC et accorde au seul jurika_app).
--
-- Tables couvertes : celles de tous les services qui migrent dans jurika_db
-- (auth, ticket, dataroom, workflow, ai, dashboard, supervision). Les tables
-- des services autres qu'auth sont protegees par to_regclass : une table
-- absente (son service n'a pas encore migre) ne peut porter aucune ligne ;
-- elle est rendue avec lignes = NULL (visible de l'appelant, jamais comptee 0
-- en silence).
--
-- Rattachements sans colonne workspace_id :
--   * demarches_migration_orphelines (ticket V24) : par ticket_id ;
--   * marketing_leads (auth V24) : converted_workspace_id efface (le prospect
--     est une donnee de l'editeur, pas du cabinet).
-- Exclus, avec leur raison :
--   * essais_registre : conserve (un essai par cabinet, RG-ESS-02 ; empreintes
--     seulement), marque supprime ;
--   * flyway_history_* : historique du schema ;
--   * referentiels communs (demarches_referentiel, demarches_justificatifs,
--     referentiel_decisions, referentiel_imports, referentiel_renvois) et
--     subscriptions (ancien catalogue des forfaits, sans workspace).
-- billing migre dans une autre base (jurika_billing) : hors de cette fonction.
--
-- Garde permanent : SuppressionEssaiCouvertureIT (auth-service) applique les
-- migrations de tous les services de jurika_db et ECHOUE si une table portant
-- workspace_id n'est pas videe ici. Toute nouvelle table de cabinet : l'ajouter
-- a cette liste par une nouvelle migration (CREATE OR REPLACE FUNCTION).
--
-- Le reglage app.current_workspace_id est pose sur le workspace supprime :
-- audit_log (FORCE ROW LEVEL SECURITY) laisse alors son proprietaire
-- supprimer les lignes de ce workspace, et de lui seul.
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

    -- Ordre de suppression : enfants d'abord (cles etrangeres), workspaces en
    -- dernier. Chaque table rend une ligne (table_nom, lignes).

    -- 1. chatbot_echanges (ai)
    IF to_regclass('public.chatbot_echanges') IS NOT NULL THEN
        DELETE FROM public.chatbot_echanges WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'chatbot_echanges'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'chatbot_echanges'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 2. chatbot_conversations (ai)
    IF to_regclass('public.chatbot_conversations') IS NOT NULL THEN
        DELETE FROM public.chatbot_conversations WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'chatbot_conversations'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'chatbot_conversations'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 3. chatbot_questions_essai (ai)
    IF to_regclass('public.chatbot_questions_essai') IS NOT NULL THEN
        DELETE FROM public.chatbot_questions_essai WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'chatbot_questions_essai'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'chatbot_questions_essai'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 4. rag_corpus_chunks (ai)
    IF to_regclass('public.rag_corpus_chunks') IS NOT NULL THEN
        DELETE FROM public.rag_corpus_chunks WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'rag_corpus_chunks'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'rag_corpus_chunks'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 5. agent_briefings (dashboard)
    IF to_regclass('public.agent_briefings') IS NOT NULL THEN
        DELETE FROM public.agent_briefings WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'agent_briefings'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'agent_briefings'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 6. dashboard_snapshots (dashboard)
    IF to_regclass('public.dashboard_snapshots') IS NOT NULL THEN
        DELETE FROM public.dashboard_snapshots WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'dashboard_snapshots'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'dashboard_snapshots'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 7. business_events (supervision)
    IF to_regclass('public.business_events') IS NOT NULL THEN
        DELETE FROM public.business_events WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'business_events'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'business_events'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 8. donnees_attendues (workflow)
    IF to_regclass('public.donnees_attendues') IS NOT NULL THEN
        DELETE FROM public.donnees_attendues WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'donnees_attendues'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'donnees_attendues'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 9. dossier_variables (workflow)
    IF to_regclass('public.dossier_variables') IS NOT NULL THEN
        DELETE FROM public.dossier_variables WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'dossier_variables'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'dossier_variables'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 10. workflow_progress (workflow)
    IF to_regclass('public.workflow_progress') IS NOT NULL THEN
        DELETE FROM public.workflow_progress WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'workflow_progress'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'workflow_progress'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 11. evenements_juridiques (workflow)
    IF to_regclass('public.evenements_juridiques') IS NOT NULL THEN
        DELETE FROM public.evenements_juridiques WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'evenements_juridiques'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'evenements_juridiques'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 12. associes (workflow)
    IF to_regclass('public.associes') IS NOT NULL THEN
        DELETE FROM public.associes WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'associes'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'associes'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 13. representants (workflow)
    IF to_regclass('public.representants') IS NOT NULL THEN
        DELETE FROM public.representants WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'representants'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'representants'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 14. fiche_juridique (workflow)
    IF to_regclass('public.fiche_juridique') IS NOT NULL THEN
        DELETE FROM public.fiche_juridique WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'fiche_juridique'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'fiche_juridique'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 15. ticket_document_snapshots (ticket)
    IF to_regclass('public.ticket_document_snapshots') IS NOT NULL THEN
        DELETE FROM public.ticket_document_snapshots WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'ticket_document_snapshots'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'ticket_document_snapshots'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 16. ticket_demarche_justificatifs (ticket)
    IF to_regclass('public.ticket_demarche_justificatifs') IS NOT NULL THEN
        DELETE FROM public.ticket_demarche_justificatifs WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'ticket_demarche_justificatifs'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'ticket_demarche_justificatifs'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 17. ticket_demarche_evenements (ticket)
    IF to_regclass('public.ticket_demarche_evenements') IS NOT NULL THEN
        DELETE FROM public.ticket_demarche_evenements WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'ticket_demarche_evenements'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'ticket_demarche_evenements'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 18. ticket_demarche_delais (ticket)
    IF to_regclass('public.ticket_demarche_delais') IS NOT NULL THEN
        DELETE FROM public.ticket_demarche_delais WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'ticket_demarche_delais'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'ticket_demarche_delais'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 19. ticket_demarches (ticket)
    IF to_regclass('public.ticket_demarches') IS NOT NULL THEN
        DELETE FROM public.ticket_demarches WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'ticket_demarches'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'ticket_demarches'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 20. dataroom_visibilite_evenements (dataroom)
    IF to_regclass('public.dataroom_visibilite_evenements') IS NOT NULL THEN
        DELETE FROM public.dataroom_visibilite_evenements WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'dataroom_visibilite_evenements'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'dataroom_visibilite_evenements'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 21. dataroom_client_access_log (dataroom)
    IF to_regclass('public.dataroom_client_access_log') IS NOT NULL THEN
        DELETE FROM public.dataroom_client_access_log WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'dataroom_client_access_log'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'dataroom_client_access_log'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 22. dataroom_wopi_verrous (dataroom)
    IF to_regclass('public.dataroom_wopi_verrous') IS NOT NULL THEN
        DELETE FROM public.dataroom_wopi_verrous WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'dataroom_wopi_verrous'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'dataroom_wopi_verrous'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 23. dataroom_wopi_sessions (dataroom)
    IF to_regclass('public.dataroom_wopi_sessions') IS NOT NULL THEN
        DELETE FROM public.dataroom_wopi_sessions WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'dataroom_wopi_sessions'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'dataroom_wopi_sessions'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 24. dataroom_acces_client_historique (dataroom)
    IF to_regclass('public.dataroom_acces_client_historique') IS NOT NULL THEN
        DELETE FROM public.dataroom_acces_client_historique WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'dataroom_acces_client_historique'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'dataroom_acces_client_historique'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 25. dataroom_demandes_client (dataroom)
    IF to_regclass('public.dataroom_demandes_client') IS NOT NULL THEN
        DELETE FROM public.dataroom_demandes_client WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'dataroom_demandes_client'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'dataroom_demandes_client'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 26. dataroom_depots (dataroom)
    IF to_regclass('public.dataroom_depots') IS NOT NULL THEN
        DELETE FROM public.dataroom_depots WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'dataroom_depots'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'dataroom_depots'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 27. dataroom_settings (dataroom)
    IF to_regclass('public.dataroom_settings') IS NOT NULL THEN
        DELETE FROM public.dataroom_settings WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'dataroom_settings'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'dataroom_settings'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 28. dataroom_objets_supprimes_v25 (dataroom)
    IF to_regclass('public.dataroom_objets_supprimes_v25') IS NOT NULL THEN
        DELETE FROM public.dataroom_objets_supprimes_v25 WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'dataroom_objets_supprimes_v25'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'dataroom_objets_supprimes_v25'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 29. dataroom_documents (dataroom)
    IF to_regclass('public.dataroom_documents') IS NOT NULL THEN
        DELETE FROM public.dataroom_documents WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'dataroom_documents'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'dataroom_documents'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 30. ticket_decisions (ticket)
    IF to_regclass('public.ticket_decisions') IS NOT NULL THEN
        DELETE FROM public.ticket_decisions WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'ticket_decisions'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'ticket_decisions'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 31. ticket_notes (ticket)
    IF to_regclass('public.ticket_notes') IS NOT NULL THEN
        DELETE FROM public.ticket_notes WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'ticket_notes'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'ticket_notes'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 32. ticket_comments (ticket)
    IF to_regclass('public.ticket_comments') IS NOT NULL THEN
        DELETE FROM public.ticket_comments WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'ticket_comments'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'ticket_comments'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 33. ticket_debours (ticket)
    IF to_regclass('public.ticket_debours') IS NOT NULL THEN
        DELETE FROM public.ticket_debours WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'ticket_debours'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'ticket_debours'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 34. alertes_delais_notifiees (ticket)
    IF to_regclass('public.alertes_delais_notifiees') IS NOT NULL THEN
        DELETE FROM public.alertes_delais_notifiees WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'alertes_delais_notifiees'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'alertes_delais_notifiees'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 35. deadlines (ticket)
    IF to_regclass('public.deadlines') IS NOT NULL THEN
        DELETE FROM public.deadlines WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'deadlines'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'deadlines'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 36. documents (ticket)
    IF to_regclass('public.documents') IS NOT NULL THEN
        DELETE FROM public.documents WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'documents'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'documents'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 37. dossier_identifiants_historique (ticket)
    IF to_regclass('public.dossier_identifiants_historique') IS NOT NULL THEN
        DELETE FROM public.dossier_identifiants_historique WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'dossier_identifiants_historique'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'dossier_identifiants_historique'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 38. dossier_reaffectations (ticket)
    IF to_regclass('public.dossier_reaffectations') IS NOT NULL THEN
        DELETE FROM public.dossier_reaffectations WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'dossier_reaffectations'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'dossier_reaffectations'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 39. dossier_transfert_requests (ticket)
    IF to_regclass('public.dossier_transfert_requests') IS NOT NULL THEN
        DELETE FROM public.dossier_transfert_requests WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'dossier_transfert_requests'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'dossier_transfert_requests'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 40. dossier_tp_versions (ticket)
    IF to_regclass('public.dossier_tp_versions') IS NOT NULL THEN
        DELETE FROM public.dossier_tp_versions WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'dossier_tp_versions'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'dossier_tp_versions'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 41. succursales (ticket)
    IF to_regclass('public.succursales') IS NOT NULL THEN
        DELETE FROM public.succursales WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'succursales'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'succursales'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 42. delais_greffe_cabinet (ticket)
    IF to_regclass('public.delais_greffe_cabinet') IS NOT NULL THEN
        DELETE FROM public.delais_greffe_cabinet WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'delais_greffe_cabinet'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'delais_greffe_cabinet'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 43. demarches_migration_orphelines (ticket) : sans workspace_id, rattachee par ticket_id (rapport de
    --     migration de ticket V24) ; videe avant tickets.
    IF to_regclass('public.demarches_migration_orphelines') IS NOT NULL THEN
        DELETE FROM public.demarches_migration_orphelines o
         WHERE o.ticket_id IN (SELECT t.id FROM public.tickets t WHERE t.workspace_id = p_workspace);
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'demarches_migration_orphelines'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'demarches_migration_orphelines'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 44. tickets (ticket)
    IF to_regclass('public.tickets') IS NOT NULL THEN
        DELETE FROM public.tickets WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'tickets'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'tickets'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 45. entreprise_dossiers (ticket)
    IF to_regclass('public.entreprise_dossiers') IS NOT NULL THEN
        DELETE FROM public.entreprise_dossiers WHERE workspace_id = p_workspace;
        GET DIAGNOSTICS v_n = ROW_COUNT;
        table_nom := 'entreprise_dossiers'; lignes := v_n; RETURN NEXT;
    ELSE
        table_nom := 'entreprise_dossiers'; lignes := NULL; RETURN NEXT;
    END IF;

    -- 46. audit_log (auth)
    DELETE FROM public.audit_log WHERE workspace_id = p_workspace;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    table_nom := 'audit_log'; lignes := v_n; RETURN NEXT;

    -- 47. essai_conditions_acceptations (auth)
    DELETE FROM public.essai_conditions_acceptations WHERE workspace_id = p_workspace;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    table_nom := 'essai_conditions_acceptations'; lignes := v_n; RETURN NEXT;

    -- 48. aides_masquees (auth)
    DELETE FROM public.aides_masquees WHERE workspace_id = p_workspace;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    table_nom := 'aides_masquees'; lignes := v_n; RETURN NEXT;

    -- 49. alertes_quota_notifiees (auth)
    DELETE FROM public.alertes_quota_notifiees WHERE workspace_id = p_workspace;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    table_nom := 'alertes_quota_notifiees'; lignes := v_n; RETURN NEXT;

    -- 50. trial_email_log (auth)
    DELETE FROM public.trial_email_log WHERE workspace_id = p_workspace;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    table_nom := 'trial_email_log'; lignes := v_n; RETURN NEXT;

    -- 51. workspace_allowed_origins (auth)
    DELETE FROM public.workspace_allowed_origins WHERE workspace_id = p_workspace;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    table_nom := 'workspace_allowed_origins'; lignes := v_n; RETURN NEXT;

    -- 52. recovery_codes (auth)
    DELETE FROM public.recovery_codes WHERE workspace_id = p_workspace;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    table_nom := 'recovery_codes'; lignes := v_n; RETURN NEXT;

    -- 53. sms_otp_codes (auth)
    DELETE FROM public.sms_otp_codes WHERE workspace_id = p_workspace;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    table_nom := 'sms_otp_codes'; lignes := v_n; RETURN NEXT;

    -- 54. email_verification_tokens (auth)
    DELETE FROM public.email_verification_tokens WHERE workspace_id = p_workspace;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    table_nom := 'email_verification_tokens'; lignes := v_n; RETURN NEXT;

    -- 55. password_reset_tokens (auth)
    DELETE FROM public.password_reset_tokens WHERE workspace_id = p_workspace;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    table_nom := 'password_reset_tokens'; lignes := v_n; RETURN NEXT;

    -- 56. refresh_tokens (auth)
    DELETE FROM public.refresh_tokens WHERE workspace_id = p_workspace;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    table_nom := 'refresh_tokens'; lignes := v_n; RETURN NEXT;

    -- 57. essai_demandes (auth)
    DELETE FROM public.essai_demandes WHERE workspace_id = p_workspace;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    table_nom := 'essai_demandes'; lignes := v_n; RETURN NEXT;

    -- 58. marketing_leads (auth) : prospect du site (donnee de l'editeur, pas du cabinet) ;
    --     seul le lien vers le cabinet supprime est efface (converted_workspace_id).
    UPDATE public.marketing_leads SET converted_workspace_id = NULL WHERE converted_workspace_id = p_workspace;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    table_nom := 'marketing_leads'; lignes := v_n; RETURN NEXT;

    -- 59. users (auth)
    DELETE FROM public.users WHERE workspace_id = p_workspace;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    table_nom := 'users'; lignes := v_n; RETURN NEXT;

    -- 60. workspaces (auth) : le cabinet lui-meme, en dernier.
    DELETE FROM public.workspaces WHERE id = p_workspace;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    table_nom := 'workspaces'; lignes := v_n; RETURN NEXT;

    UPDATE essais_registre SET supprime_le = now() WHERE workspace_id = p_workspace;
END
$$;

REVOKE EXECUTE ON FUNCTION essai_supprimer_definitivement(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION essai_supprimer_definitivement(uuid) TO jurika_app;
