-- Lot L0 (securite serveur), etapes E13a et E13b -- auth-service.
--
-- Fonctions SECURITY DEFINER pour les SEULS chemins qui doivent lire au-dela du
-- workspace courant (inventaire L0_inventaire_sans_workspace.md, section 2) :
-- avant de connaitre le workspace (connexion par code, jetons opaques, unicite
-- globale du code), cote super-admin (vues transverses par nature) et pour
-- l'entretien planifie. Le role d'execution jurika_app est soumis a la RLS ; ces
-- fonctions s'executent avec les droits de leur proprietaire (le role qui
-- migre), qui n'y est pas soumis.
--
-- Regles (approbation du 2026-10-07) : une fonction par usage, parametres
-- precis, colonnes minimales, aucun SQL dynamique, search_path fixe
-- (public puis pg_temp), EXECUTE retire a PUBLIC puis accorde au seul
-- jurika_app. L'autorisation metier (role SUPER_ADMIN, etc.) reste verifiee par
-- le serveur avant l'appel (@PreAuthorize).
--
-- Echoue explicitement si jurika_app est absent (comme V32).

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        RAISE EXCEPTION 'Role jurika_app absent : le creer avant le demarrage des services '
            '(init-db.sh ou rattrapage-role-applicatif.sh, cf. ROLE_APPLICATIF_RDS.md)';
    END IF;
END
$$;

-- ---------------------------------------------------------------- E13a (publics)

-- P1 a P4 : connexion, code de secours, demande de reinitialisation, renvoi du
-- lien de verification, controle du code. Le code workspace est la cle d'entree.
CREATE OR REPLACE FUNCTION auth_workspace_par_code(p_code text)
RETURNS uuid
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT id FROM workspaces WHERE code = p_code
$$;

-- P5 : confirmation de reinitialisation. L'empreinte d'un jeton aleatoire est un
-- secret : la recherche ne revele rien.
CREATE OR REPLACE FUNCTION auth_workspace_du_jeton_reset(p_empreinte text)
RETURNS uuid
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT workspace_id FROM password_reset_tokens WHERE token_hash = p_empreinte
$$;

-- P6 : verification de l'email.
CREATE OR REPLACE FUNCTION auth_workspace_du_jeton_verification(p_empreinte text)
RETURNS uuid
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT workspace_id FROM email_verification_tokens WHERE token_hash = p_empreinte
$$;

-- P7 : renouvellement de session (refresh token opaque).
CREATE OR REPLACE FUNCTION auth_workspace_du_jeton_refresh(p_empreinte text)
RETURNS uuid
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT workspace_id FROM refresh_tokens WHERE token_hash = p_empreinte
$$;

-- P9 : unicite GLOBALE du code workspace a l'inscription.
CREATE OR REPLACE FUNCTION auth_code_workspace_existe(p_code text)
RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT EXISTS (SELECT 1 FROM workspaces WHERE code = p_code)
$$;

-- ---------------------------------------------------------- E13b (super-admin)

-- S1 : liste des workspaces (AdminWorkspaceController#list).
CREATE OR REPLACE FUNCTION admin_liste_workspaces()
RETURNS TABLE (id uuid, code varchar, name varchar, contact_email varchar, status varchar,
               trial_status varchar, selected_plan varchar, created_at timestamptz,
               employes bigint, clients bigint)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT w.id, w.code, w.name, w.contact_email, w.status,
           w.trial_status, w.selected_plan, w.created_at,
           (SELECT COUNT(*) FROM users u
              WHERE u.workspace_id = w.id AND u.role IN ('EMPLOYE','SUPERVISEUR')),
           (SELECT COUNT(*) FROM users u
              WHERE u.workspace_id = w.id AND u.role = 'CLIENT')
    FROM workspaces w
    ORDER BY w.created_at DESC
$$;

-- S1 : stockage par workspace. Les tables de dataroom sont creees APRES auth sur
-- une base vierge : plpgsql (corps non valide a la creation).
CREATE OR REPLACE FUNCTION admin_stockage_par_workspace()
RETURNS TABLE (workspace_id uuid, octets bigint, documents bigint)
LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    RETURN QUERY
    SELECT t.workspace_id, SUM(t.size_bytes)::bigint, COUNT(*)::bigint
    FROM (
        SELECT d.workspace_id, d.size_bytes FROM dataroom_documents d WHERE d.is_current = true
        UNION ALL
        SELECT p.workspace_id, p.size_bytes FROM dataroom_depots p WHERE p.deleted_at IS NULL
    ) t
    WHERE t.workspace_id IS NOT NULL
    GROUP BY t.workspace_id;
END
$$;

-- S1 : liste paginee et filtree des utilisateurs (AdminUsersController#list).
-- Filtres optionnels (NULL = sans filtre), sans SQL dynamique ; total en colonne.
CREATE OR REPLACE FUNCTION admin_liste_utilisateurs(p_recherche text, p_role text,
                                                    p_workspace uuid, p_limite integer,
                                                    p_decalage integer)
RETURNS TABLE (id uuid, first_name varchar, last_name varchar, login_email varchar,
               contact_email varchar, role varchar, status varchar, totp_enabled boolean,
               created_at timestamptz, last_login_at timestamptz, workspace_id uuid,
               ws_code varchar, ws_name varchar, total bigint)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT u.id, u.first_name, u.last_name, u.login_email, u.contact_email,
           u.role, u.status, u.totp_enabled, u.created_at, u.last_login_at,
           u.workspace_id, w.code, w.name,
           COUNT(*) OVER ()
    FROM users u
    LEFT JOIN workspaces w ON w.id = u.workspace_id
    WHERE (p_recherche IS NULL
           OR u.first_name ILIKE '%' || p_recherche || '%'
           OR u.last_name ILIKE '%' || p_recherche || '%'
           OR u.login_email ILIKE '%' || p_recherche || '%'
           OR u.contact_email ILIKE '%' || p_recherche || '%')
      AND (p_role IS NULL OR u.role = p_role)
      AND (p_workspace IS NULL OR u.workspace_id = p_workspace)
    ORDER BY u.created_at DESC
    LIMIT p_limite OFFSET p_decalage
$$;

-- S3 : workspace d'un utilisateur (AdminUsersController#changeStatus).
CREATE OR REPLACE FUNCTION admin_workspace_de_utilisateur(p_utilisateur uuid)
RETURNS uuid
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT workspace_id FROM users WHERE id = p_utilisateur
$$;

-- S4 : identifiants des utilisateurs d'un role (filtre du journal d'audit).
CREATE OR REPLACE FUNCTION admin_utilisateurs_par_role(p_role text)
RETURNS SETOF uuid
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT id FROM users WHERE role = p_role
$$;

-- J1 : purge planifiee des refresh tokens expires (RefreshTokenCleanupJob).
CREATE OR REPLACE FUNCTION auth_purge_jetons_refresh(p_limite timestamptz)
RETURNS integer
LANGUAGE sql VOLATILE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    WITH supprimes AS (
        DELETE FROM refresh_tokens WHERE expires_at < p_limite RETURNING 1
    )
    SELECT COUNT(*)::integer FROM supprimes
$$;

-- --------------------------------------------------------------------- droits

REVOKE EXECUTE ON FUNCTION auth_workspace_par_code(text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION auth_workspace_du_jeton_reset(text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION auth_workspace_du_jeton_verification(text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION auth_workspace_du_jeton_refresh(text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION auth_code_workspace_existe(text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION admin_liste_workspaces() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION admin_stockage_par_workspace() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION admin_liste_utilisateurs(text, text, uuid, integer, integer) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION admin_workspace_de_utilisateur(uuid) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION admin_utilisateurs_par_role(text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION auth_purge_jetons_refresh(timestamptz) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION auth_workspace_par_code(text) TO jurika_app;
GRANT EXECUTE ON FUNCTION auth_workspace_du_jeton_reset(text) TO jurika_app;
GRANT EXECUTE ON FUNCTION auth_workspace_du_jeton_verification(text) TO jurika_app;
GRANT EXECUTE ON FUNCTION auth_workspace_du_jeton_refresh(text) TO jurika_app;
GRANT EXECUTE ON FUNCTION auth_code_workspace_existe(text) TO jurika_app;
GRANT EXECUTE ON FUNCTION admin_liste_workspaces() TO jurika_app;
GRANT EXECUTE ON FUNCTION admin_stockage_par_workspace() TO jurika_app;
GRANT EXECUTE ON FUNCTION admin_liste_utilisateurs(text, text, uuid, integer, integer) TO jurika_app;
GRANT EXECUTE ON FUNCTION admin_workspace_de_utilisateur(uuid) TO jurika_app;
GRANT EXECUTE ON FUNCTION admin_utilisateurs_par_role(text) TO jurika_app;
GRANT EXECUTE ON FUNCTION auth_purge_jetons_refresh(timestamptz) TO jurika_app;
