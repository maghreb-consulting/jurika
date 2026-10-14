-- =====================================================================
-- JURIKA billing V18 -- Lot L7, etape E14 : donnees de facturation d'un essai
-- supprime definitivement (RG-ESS-10) et conservation comptable.
--
-- Decision de l'utilisateur du 2026-10-14 : "d'un essai supprime, on supprime
-- les paiements non aboutis et on conserve 10 ans les paiements aboutis et les
-- factures (obligation comptable)."
--
-- auth-service conduit la suppression (CycleEssaiService) ; jurika_billing est
-- une AUTRE base, hors de la fonction essai_supprimer_definitivement (auth
-- V45). auth appelle billing (DELETE /internal/essais/{ws}/paiements) AVANT de
-- supprimer quoi que ce soit dans jurika_db : un refus ou un echec de billing
-- arrete tout.
--
-- 1. essai_facturation_supprimer(ws) : une ligne par table portant le cabinet
--    (sort SUPPRIMEE, CONSERVEE_10_ANS, CONSERVEE_DUREE_A_DECIDER, TRACE).
--    Classement (garde : SuppressionEssaiFacturationIT, qui echoue si une table
--    portant workspace_id, ou liee a une telle table, n'est pas classee) :
--      payments          PENDING, FAILED, CANCELLED (non aboutis) : supprimes ;
--                        COMPLETED (aboutis) : conserves 10 ans ;
--      cmi_retours       (sans workspace_id, lies par payment_id / oid) : suivent
--                        leur paiement ;
--      factures          conservees 10 ans (mentions figees et PDF dans la ligne) ;
--      invoices          (factures de l'ancien Stripe) : conservees 10 ans ;
--      subscriptions     supprimees (refus si un abonnement est actif) ;
--      payment_methods   (references de carte Stripe) : supprimees ;
--      conditions_particulieres, contrat_acceptations, contrat_resiliations,
--      contrat_renouvellements, contrat_avis, contrat_reponses_versions,
--      impaye_journal    inalterables (V13, V15, V16) : conserves, duree
--                        A_DECIDER (directeur) ; jamais supprimes ici ;
--      conservation_comptable : registre (trace) de cette conservation.
--    Refus (rien n'est supprime) : abonnement actif (JE001) ; paiement acquis
--    dont la facture ou l'activation est encore a traiter (JE002).
--    Les paiements du cabinet sont verrouilles (FOR UPDATE) avant les
--    controles : une confirmation du CMI concurrente (qui verrouille la meme
--    ligne) passe soit avant (paiement acquis, abonnement actif : refus), soit
--    apres (commande inconnue : reponse FAILURE, aucune capture).
--
-- 2. conservation_comptable : une ligne par essai supprime ayant laisse des
--    pieces conservees ; echeance = date (Casablanca) de la suppression + 10
--    ans (contrainte). Inalterable (declencheurs), sauf la pose de la purge,
--    une fois, l'echeance passee.
--
-- 3. conservation_comptable_purger(ws) : SECURITY DEFINER ; garde-fou dans la
--    fonction : refuse avant l'echeance (JE012), si deja purge (JE011) ou sans
--    registre (JE010). Supprime les paiements aboutis, leurs retours du CMI,
--    les factures et les factures Stripe du cabinet, et rien d'autre ; la purge
--    est tracee dans la ligne du registre (date, nombres).
--    Inalterabilite des factures : la table factures n'a pas de declencheur ;
--    elle est protegee par les droits (jurika_app : ni UPDATE ni DELETE, V8).
--    Tant que billing se connecte en proprietaire (dette L0), ces droits ne
--    s'appliquent pas a l'application ; apres la bascule, la purge reste
--    possible par cette seule fonction.
--
-- Isolation : RLS par workspace sur le registre (meme politique que V15) et
-- filtrage applicatif explicite ; droits jurika_app au plus juste.
-- =====================================================================

CREATE TABLE IF NOT EXISTS conservation_comptable (
    workspace_id                UUID          PRIMARY KEY,
    essai_supprime_le           TIMESTAMPTZ   NOT NULL,
    echeance                    DATE          NOT NULL,
    paiements_conserves         INTEGER       NOT NULL CHECK (paiements_conserves >= 0),
    retours_cmi_conserves       INTEGER       NOT NULL CHECK (retours_cmi_conserves >= 0),
    factures_conservees         INTEGER       NOT NULL CHECK (factures_conservees >= 0),
    factures_stripe_conservees  INTEGER       NOT NULL CHECK (factures_stripe_conservees >= 0),
    autres_conservees           INTEGER       NOT NULL CHECK (autres_conservees >= 0),
    autres_duree                VARCHAR(20)   NOT NULL CHECK (autres_duree = 'A_DECIDER'),
    purge_le                    TIMESTAMPTZ,
    purge_paiements             INTEGER,
    purge_retours_cmi           INTEGER,
    purge_factures              INTEGER,
    purge_factures_stripe       INTEGER,
    CONSTRAINT ck_conservation_dix_ans CHECK (
        echeance = ((essai_supprime_le AT TIME ZONE 'Africa/Casablanca')::date + INTERVAL '10 years')::date),
    CONSTRAINT ck_conservation_purge_complete CHECK (
        (purge_le IS NULL) = (purge_paiements IS NULL)
        AND (purge_le IS NULL) = (purge_retours_cmi IS NULL)
        AND (purge_le IS NULL) = (purge_factures IS NULL)
        AND (purge_le IS NULL) = (purge_factures_stripe IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_conservation_comptable_a_purger
    ON conservation_comptable (echeance) WHERE purge_le IS NULL;

COMMENT ON TABLE conservation_comptable IS
    'Lot L7 (V18) -- pieces comptables d''un essai supprime conservees 10 ans (paiements aboutis, factures) ; '
    'autres_conservees : preuves du contrat et journal des impayes, duree A_DECIDER. Inalterable sauf la purge.';
COMMENT ON COLUMN conservation_comptable.echeance IS
    'Date (Casablanca) de la suppression de l''essai + 10 ans ; purge possible le lendemain.';
COMMENT ON COLUMN conservation_comptable.autres_duree IS
    'Duree de conservation des preuves du contrat et du journal des impayes d''un essai : A_DECIDER (directeur).';

-- ---------------------------------------------------------------------
-- Inalterabilite du registre : aucune suppression ; une seule mise a jour,
-- la pose de la purge, l'echeance passee, sans toucher aux autres colonnes.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION conservation_comptable_inalterable()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP <> 'UPDATE' THEN
        RAISE EXCEPTION 'Registre de conservation comptable inalterable : % refuse', TG_OP
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    IF OLD.purge_le IS NULL
       AND NEW.purge_le IS NOT NULL
       AND OLD.echeance < (now() AT TIME ZONE 'Africa/Casablanca')::date
       AND NEW.workspace_id = OLD.workspace_id
       AND NEW.essai_supprime_le = OLD.essai_supprime_le
       AND NEW.echeance = OLD.echeance
       AND NEW.paiements_conserves = OLD.paiements_conserves
       AND NEW.retours_cmi_conserves = OLD.retours_cmi_conserves
       AND NEW.factures_conservees = OLD.factures_conservees
       AND NEW.factures_stripe_conservees = OLD.factures_stripe_conservees
       AND NEW.autres_conservees = OLD.autres_conservees
       AND NEW.autres_duree = OLD.autres_duree THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Registre de conservation comptable inalterable (seule la purge, l''echeance passee, le complete) : % refuse',
        TG_OP
        USING ERRCODE = 'insufficient_privilege';
END
$$;

DROP TRIGGER IF EXISTS trg_conservation_comptable_inalterable ON conservation_comptable;
CREATE TRIGGER trg_conservation_comptable_inalterable
    BEFORE UPDATE OR DELETE ON conservation_comptable
    FOR EACH ROW EXECUTE FUNCTION conservation_comptable_inalterable();

DROP TRIGGER IF EXISTS trg_conservation_comptable_sans_truncate ON conservation_comptable;
CREATE TRIGGER trg_conservation_comptable_sans_truncate
    BEFORE TRUNCATE ON conservation_comptable
    FOR EACH STATEMENT EXECUTE FUNCTION conservation_comptable_inalterable();

ALTER TABLE conservation_comptable ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS conservation_comptable_isolation ON conservation_comptable;
CREATE POLICY conservation_comptable_isolation ON conservation_comptable FOR ALL
    USING (current_setting('app.facturation_portee', true) = 'globale'
           OR workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid)
    WITH CHECK (current_setting('app.facturation_portee', true) = 'globale'
           OR workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid);

-- ---------------------------------------------------------------------
-- 1. Donnees de facturation d'un essai supprime.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION essai_facturation_supprimer(p_ws uuid)
RETURNS TABLE (table_nom text, sort text, lignes bigint)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_non_aboutis   bigint[];
    v_aboutis       bigint[];
    v_oids          text[];
    v_n             bigint;
    v_pay_c         bigint;
    v_cmi_c         bigint;
    v_fac_c         bigint;
    v_inv_c         bigint;
    v_autres        bigint := 0;
    v_cp            bigint;
    v_acc           bigint;
    v_res           bigint;
    v_ren           bigint;
    v_avis          bigint;
    v_rep           bigint;
    v_imp           bigint;
BEGIN
    IF p_ws IS NULL THEN
        RAISE EXCEPTION 'Cabinet absent' USING ERRCODE = 'JE000';
    END IF;

    -- Verrou des paiements du cabinet avant tout controle (confirmation du CMI concurrente).
    PERFORM 1 FROM payments p WHERE p.workspace_id = p_ws FOR UPDATE;

    IF EXISTS (SELECT 1 FROM subscriptions s WHERE s.workspace_id = p_ws AND s.status = 'active') THEN
        RAISE EXCEPTION 'Abonnement actif : ce cabinet n''est pas un essai a supprimer' USING ERRCODE = 'JE001';
    END IF;
    IF EXISTS (SELECT 1 FROM payments p WHERE p.workspace_id = p_ws AND p.status = 'COMPLETED'
               AND (p.activation_propagee = FALSE
                    OR NOT EXISTS (SELECT 1 FROM factures f WHERE f.payment_id = p.id))) THEN
        RAISE EXCEPTION 'Paiement acquis dont la facture ou l''activation est encore a traiter'
            USING ERRCODE = 'JE002';
    END IF;

    SELECT COALESCE(array_agg(p.id), '{}'), COALESCE(array_agg(p.cmi_oid) FILTER (WHERE p.cmi_oid IS NOT NULL), '{}')
      INTO v_non_aboutis, v_oids
      FROM payments p
     WHERE p.workspace_id = p_ws AND p.status IN ('PENDING', 'FAILED', 'CANCELLED');
    SELECT COALESCE(array_agg(p.id), '{}') INTO v_aboutis
      FROM payments p WHERE p.workspace_id = p_ws AND p.status = 'COMPLETED';
    IF EXISTS (SELECT 1 FROM payments p WHERE p.workspace_id = p_ws
               AND p.status NOT IN ('PENDING', 'FAILED', 'CANCELLED', 'COMPLETED')) THEN
        RAISE EXCEPTION 'Statut de paiement non classe' USING ERRCODE = 'JE003';
    END IF;

    -- Retours du CMI des paiements non aboutis (par paiement, ou par commande quand le lien est absent).
    DELETE FROM cmi_retours r
     WHERE r.payment_id = ANY (v_non_aboutis)
        OR (r.payment_id IS NULL AND r.oid = ANY (v_oids));
    GET DIAGNOSTICS v_n = ROW_COUNT;
    table_nom := 'cmi_retours'; sort := 'SUPPRIMEE'; lignes := v_n; RETURN NEXT;

    DELETE FROM payments p WHERE p.id = ANY (v_non_aboutis);
    GET DIAGNOSTICS v_n = ROW_COUNT;
    table_nom := 'payments'; sort := 'SUPPRIMEE'; lignes := v_n; RETURN NEXT;

    DELETE FROM subscriptions s WHERE s.workspace_id = p_ws;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    table_nom := 'subscriptions'; sort := 'SUPPRIMEE'; lignes := v_n; RETURN NEXT;

    DELETE FROM payment_methods m WHERE m.workspace_id = p_ws;
    GET DIAGNOSTICS v_n = ROW_COUNT;
    table_nom := 'payment_methods'; sort := 'SUPPRIMEE'; lignes := v_n; RETURN NEXT;

    -- Conserve 10 ans.
    v_pay_c := cardinality(v_aboutis);
    table_nom := 'payments'; sort := 'CONSERVEE_10_ANS'; lignes := v_pay_c; RETURN NEXT;
    SELECT count(*) INTO v_cmi_c FROM cmi_retours r WHERE r.payment_id = ANY (v_aboutis);
    table_nom := 'cmi_retours'; sort := 'CONSERVEE_10_ANS'; lignes := v_cmi_c; RETURN NEXT;
    SELECT count(*) INTO v_fac_c FROM factures f WHERE f.workspace_id = p_ws;
    table_nom := 'factures'; sort := 'CONSERVEE_10_ANS'; lignes := v_fac_c; RETURN NEXT;
    SELECT count(*) INTO v_inv_c FROM invoices i WHERE i.workspace_id = p_ws;
    table_nom := 'invoices'; sort := 'CONSERVEE_10_ANS'; lignes := v_inv_c; RETURN NEXT;

    -- Preuves du contrat et journal des impayes : inalterables, conserves, duree A_DECIDER.
    SELECT count(*) INTO v_cp FROM conditions_particulieres t WHERE t.workspace_id = p_ws;
    table_nom := 'conditions_particulieres'; sort := 'CONSERVEE_DUREE_A_DECIDER'; lignes := v_cp; RETURN NEXT;
    SELECT count(*) INTO v_acc FROM contrat_acceptations t WHERE t.workspace_id = p_ws;
    table_nom := 'contrat_acceptations'; sort := 'CONSERVEE_DUREE_A_DECIDER'; lignes := v_acc; RETURN NEXT;
    SELECT count(*) INTO v_res FROM contrat_resiliations t WHERE t.workspace_id = p_ws;
    table_nom := 'contrat_resiliations'; sort := 'CONSERVEE_DUREE_A_DECIDER'; lignes := v_res; RETURN NEXT;
    SELECT count(*) INTO v_ren FROM contrat_renouvellements t WHERE t.workspace_id = p_ws;
    table_nom := 'contrat_renouvellements'; sort := 'CONSERVEE_DUREE_A_DECIDER'; lignes := v_ren; RETURN NEXT;
    SELECT count(*) INTO v_avis FROM contrat_avis t WHERE t.workspace_id = p_ws;
    table_nom := 'contrat_avis'; sort := 'CONSERVEE_DUREE_A_DECIDER'; lignes := v_avis; RETURN NEXT;
    SELECT count(*) INTO v_rep FROM contrat_reponses_versions t WHERE t.workspace_id = p_ws;
    table_nom := 'contrat_reponses_versions'; sort := 'CONSERVEE_DUREE_A_DECIDER'; lignes := v_rep; RETURN NEXT;
    SELECT count(*) INTO v_imp FROM impaye_journal t WHERE t.workspace_id = p_ws;
    table_nom := 'impaye_journal'; sort := 'CONSERVEE_DUREE_A_DECIDER'; lignes := v_imp; RETURN NEXT;
    v_autres := v_cp + v_acc + v_res + v_ren + v_avis + v_rep + v_imp;

    -- Registre : une ligne des qu'une piece est conservee (un second appel garde la premiere date).
    v_n := 0;
    IF v_pay_c + v_cmi_c + v_fac_c + v_inv_c + v_autres > 0 THEN
        INSERT INTO conservation_comptable (workspace_id, essai_supprime_le, echeance, paiements_conserves,
                retours_cmi_conserves, factures_conservees, factures_stripe_conservees, autres_conservees, autres_duree)
        VALUES (p_ws, now(), ((now() AT TIME ZONE 'Africa/Casablanca')::date + INTERVAL '10 years')::date,
                v_pay_c, v_cmi_c, v_fac_c, v_inv_c, v_autres, 'A_DECIDER')
        ON CONFLICT (workspace_id) DO NOTHING;
        v_n := 1;
    END IF;
    table_nom := 'conservation_comptable'; sort := 'TRACE'; lignes := v_n; RETURN NEXT;
END
$$;

-- ---------------------------------------------------------------------
-- 3. Purge a l'echeance (garde-fou dans la fonction).
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION conservation_comptable_purger(p_ws uuid)
RETURNS TABLE (table_nom text, lignes bigint)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_reg       conservation_comptable%ROWTYPE;
    v_aboutis   bigint[];
    v_cmi       bigint;
    v_fac       bigint;
    v_inv       bigint;
    v_pay       bigint;
BEGIN
    SELECT * INTO v_reg FROM conservation_comptable c WHERE c.workspace_id = p_ws FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Aucune conservation comptable pour ce cabinet' USING ERRCODE = 'JE010';
    END IF;
    IF v_reg.purge_le IS NOT NULL THEN
        RAISE EXCEPTION 'Conservation comptable deja purgee le %', v_reg.purge_le USING ERRCODE = 'JE011';
    END IF;
    IF v_reg.echeance >= (now() AT TIME ZONE 'Africa/Casablanca')::date THEN
        RAISE EXCEPTION 'Echeance de conservation non passee (%) : purge refusee', v_reg.echeance
            USING ERRCODE = 'JE012';
    END IF;

    SELECT COALESCE(array_agg(p.id), '{}') INTO v_aboutis
      FROM payments p WHERE p.workspace_id = p_ws AND p.status = 'COMPLETED';

    DELETE FROM cmi_retours r WHERE r.payment_id = ANY (v_aboutis);
    GET DIAGNOSTICS v_cmi = ROW_COUNT;
    DELETE FROM factures f WHERE f.workspace_id = p_ws;
    GET DIAGNOSTICS v_fac = ROW_COUNT;
    DELETE FROM invoices i WHERE i.workspace_id = p_ws;
    GET DIAGNOSTICS v_inv = ROW_COUNT;
    DELETE FROM payments p WHERE p.id = ANY (v_aboutis);
    GET DIAGNOSTICS v_pay = ROW_COUNT;

    UPDATE conservation_comptable c
       SET purge_le = now(), purge_paiements = v_pay, purge_retours_cmi = v_cmi,
           purge_factures = v_fac, purge_factures_stripe = v_inv
     WHERE c.workspace_id = p_ws;

    table_nom := 'payments'; lignes := v_pay; RETURN NEXT;
    table_nom := 'cmi_retours'; lignes := v_cmi; RETURN NEXT;
    table_nom := 'factures'; lignes := v_fac; RETURN NEXT;
    table_nom := 'invoices'; lignes := v_inv; RETURN NEXT;
END
$$;

-- ---------------------------------------------------------------------
-- Droits (au plus juste). Le registre ne s'ecrit que par les fonctions.
-- ---------------------------------------------------------------------
REVOKE ALL ON conservation_comptable FROM PUBLIC;
REVOKE ALL ON FUNCTION essai_facturation_supprimer(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION conservation_comptable_purger(uuid) FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        REVOKE ALL ON conservation_comptable FROM jurika_app;
        GRANT SELECT ON conservation_comptable TO jurika_app;
        GRANT EXECUTE ON FUNCTION essai_facturation_supprimer(uuid) TO jurika_app;
        GRANT EXECUTE ON FUNCTION conservation_comptable_purger(uuid) TO jurika_app;
    END IF;
END
$$;
