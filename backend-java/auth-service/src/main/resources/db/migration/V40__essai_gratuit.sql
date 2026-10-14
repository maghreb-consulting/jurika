-- =====================================================================
-- JURIKA V40 (auth-service) -- Lot L7 (E4) : essai gratuit de 7 jours.
--
-- CDC 4.2 (RG-ESS-01 a RG-ESS-11), RG-SOU-02, RG-CTR-10.
--
--  * workspaces : colonnes du cycle de l'essai (lecture seule, avertissement
--    de suppression, prolongation unique, fin decidee par le super-admin).
--  * essai_demandes : inscriptions en attente de verification (adresse par
--    lien, telephone par code SMS). Donnee d'AVANT le cabinet (aucun
--    workspace) : pas de RLS, droits SELECT, INSERT, UPDATE seulement ; la
--    purge passe par une fonction (aucun DELETE pour jurika_app).
--  * essais_registre : un essai par cabinet (RG-ESS-02), signalements
--    (RG-ESS-03). La ligne survit a la suppression definitive du cabinet
--    (aucune cle etrangere), ne garde que des empreintes de l'adresse et du
--    telephone. RLS par workspace ; les lectures transverses passent par des
--    fonctions SECURITY DEFINER.
--  * essai_conditions_acceptations : preuve de l'acceptation des conditions
--    d'utilisation de l'essai (RG-CTR-10 selon RG-CTR-02) : version, texte,
--    empreinte, date, IP. Ni UPDATE ni DELETE pour jurika_app.
--
-- Regles des fonctions (V34) : un usage, parametres precis, colonnes
-- minimales, aucun SQL dynamique, search_path fixe, EXECUTE retire a PUBLIC
-- puis accorde au seul jurika_app.
-- =====================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        RAISE EXCEPTION 'Role jurika_app absent : le creer avant le demarrage des services '
            '(init-db.sh ou rattrapage-role-applicatif.sh, cf. ROLE_APPLICATIF_RDS.md)';
    END IF;
END
$$;

-- ------------------------------------------------------------ workspaces

ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS essai_expire_le          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS essai_avertissement_le   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS essai_prolonge_le        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS essai_prolongation_jours INTEGER,
    ADD COLUMN IF NOT EXISTS essai_fin_motif          VARCHAR(300);

COMMENT ON COLUMN workspaces.trial_ends_at IS
    'L7 E4 : fin de l essai gratuit (creation + 7 jours, RG-SOU-02), prolongeable une fois (RG-ESS-11).';
COMMENT ON COLUMN workspaces.essai_expire_le IS
    'L7 E4 : debut de la lecture seule de 30 jours (RG-ESS-10) : fin des 7 jours ou fin decidee par le super-admin.';
COMMENT ON COLUMN workspaces.essai_avertissement_le IS
    'L7 E4 : avertissement de suppression definitive envoye (courriel et application), au passage en lecture seule.';
COMMENT ON COLUMN workspaces.essai_prolonge_le IS
    'L7 E4 : prolongation unique par le super-admin (RG-ESS-11) ; NULL tant qu il n y en a pas eu.';

-- ---------------------------------------------------- demandes en attente

CREATE TABLE IF NOT EXISTS essai_demandes (
    id                       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    nom                      VARCHAR(80)   NOT NULL,
    prenom                   VARCHAR(80)   NOT NULL,
    organisation             VARCHAR(150)  NOT NULL,
    telephone                VARCHAR(20)   NOT NULL CHECK (telephone ~ '^\+[1-9][0-9]{6,14}$'),
    email                    VARCHAR(150)  NOT NULL,
    jeton_email_empreinte    CHAR(64)      NOT NULL UNIQUE,
    email_verifie_le         TIMESTAMPTZ,
    code_sms_empreinte       CHAR(64),
    code_sms_envoye_le       TIMESTAMPTZ,
    code_sms_expire_le       TIMESTAMPTZ,
    code_sms_tentatives      SMALLINT      NOT NULL DEFAULT 0,
    telephone_verifie_le     TIMESTAMPTZ,
    conditions_version       VARCHAR(60)   NOT NULL,
    conditions_empreinte     CHAR(64)      NOT NULL,
    conditions_acceptees_le  TIMESTAMPTZ   NOT NULL,
    adresse_ip               VARCHAR(45),
    appareil                 VARCHAR(64),
    user_agent               VARCHAR(500),
    cree_le                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    expire_le                TIMESTAMPTZ   NOT NULL,
    workspace_id             UUID
);

CREATE INDEX IF NOT EXISTS idx_essai_demandes_email ON essai_demandes (lower(email));
CREATE INDEX IF NOT EXISTS idx_essai_demandes_expire ON essai_demandes (expire_le);

COMMENT ON TABLE essai_demandes IS
    'L7 E4 : inscriptions a l essai en attente de verification (RG-ESS-01, RG-ESS-02). Purgees par essai_purger_demandes.';

-- ----------------------------------------------------- registre des essais

CREATE TABLE IF NOT EXISTS essais_registre (
    workspace_id            UUID          PRIMARY KEY,
    workspace_code          VARCHAR(12)   NOT NULL,
    email_empreinte         CHAR(64)      NOT NULL,
    telephone_empreinte     CHAR(64)      NOT NULL,
    adresse_ip              VARCHAR(45),
    appareil                VARCHAR(64),
    signalement             VARCHAR(300),
    signalement_examine_le  TIMESTAMPTZ,
    cree_le                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    supprime_le             TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_essais_registre_email ON essais_registre (email_empreinte);
CREATE INDEX IF NOT EXISTS idx_essais_registre_tel ON essais_registre (telephone_empreinte);
CREATE INDEX IF NOT EXISTS idx_essais_registre_ip ON essais_registre (adresse_ip);
CREATE INDEX IF NOT EXISTS idx_essais_registre_appareil ON essais_registre (appareil);

ALTER TABLE essais_registre ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS essais_registre_isolation ON essais_registre;
CREATE POLICY essais_registre_isolation ON essais_registre FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid);

COMMENT ON TABLE essais_registre IS
    'L7 E4 : un essai par cabinet (RG-ESS-02) et signalements (RG-ESS-03). Survit a la suppression du cabinet ; empreintes SHA-256 seulement.';

-- ------------------------------------------- acceptation des conditions

CREATE TABLE IF NOT EXISTS essai_conditions_acceptations (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID          NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id         UUID          REFERENCES users(id) ON DELETE CASCADE,
    version         VARCHAR(60)   NOT NULL,
    texte           TEXT          NOT NULL,
    empreinte       CHAR(64)      NOT NULL,
    acceptees_le    TIMESTAMPTZ   NOT NULL,
    adresse_ip      VARCHAR(45),
    user_agent      VARCHAR(500),
    enregistre_le   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_essai_conditions_workspace ON essai_conditions_acceptations (workspace_id);

ALTER TABLE essai_conditions_acceptations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS essai_conditions_isolation ON essai_conditions_acceptations;
CREATE POLICY essai_conditions_isolation ON essai_conditions_acceptations FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid);

-- ------------------------------------------------------------ fonctions

-- RG-ESS-02 : l'adresse ou le telephone a-t-il deja servi a un compte ou a un
-- essai, dans n'importe quel cabinet ? Ne revele que deux booleens.
CREATE OR REPLACE FUNCTION essai_identite_deja_utilisee(p_email text, p_telephone text,
                                                         p_email_empreinte text,
                                                         p_telephone_empreinte text)
RETURNS TABLE (email_utilise boolean, telephone_utilise boolean)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT
        EXISTS (SELECT 1 FROM users u
                 WHERE lower(u.email) = lower(p_email)
                    OR lower(u.contact_email) = lower(p_email)
                    OR lower(u.login_email) = lower(p_email))
        OR EXISTS (SELECT 1 FROM workspaces w WHERE lower(w.contact_email) = lower(p_email))
        OR EXISTS (SELECT 1 FROM essais_registre r WHERE r.email_empreinte = p_email_empreinte),
        EXISTS (SELECT 1 FROM users u
                 WHERE u.phone = p_telephone OR u.phone_e164 = p_telephone)
        OR EXISTS (SELECT 1 FROM essais_registre r WHERE r.telephone_empreinte = p_telephone_empreinte)
$$;

-- RG-ESS-03 : nombre d'essais deja ouverts depuis le meme reseau (IP) ou le
-- meme appareil. Un comptage, jamais un blocage.
CREATE OR REPLACE FUNCTION essai_origines_communes(p_adresse_ip text, p_appareil text)
RETURNS TABLE (meme_reseau bigint, meme_appareil bigint)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT
        (SELECT COUNT(*) FROM essais_registre r
          WHERE p_adresse_ip IS NOT NULL AND r.adresse_ip = p_adresse_ip),
        (SELECT COUNT(*) FROM essais_registre r
          WHERE p_appareil IS NOT NULL AND r.appareil = p_appareil)
$$;

-- Super-admin (RG-ESS-03, RG-ESS-11) : liste des essais, y compris supprimes.
CREATE OR REPLACE FUNCTION admin_liste_essais()
RETURNS TABLE (workspace_id uuid, workspace_code varchar, name varchar, contact_email varchar,
               status varchar, trial_status varchar, trial_started_at timestamptz,
               trial_ends_at timestamptz, essai_expire_le timestamptz,
               essai_avertissement_le timestamptz, essai_prolonge_le timestamptz,
               essai_prolongation_jours integer, essai_fin_motif varchar,
               subscription_status varchar, adresse_ip varchar, appareil varchar,
               signalement varchar, signalement_examine_le timestamptz,
               cree_le timestamptz, supprime_le timestamptz)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT r.workspace_id, r.workspace_code, w.name, w.contact_email, w.status,
           w.trial_status, w.trial_started_at, w.trial_ends_at, w.essai_expire_le,
           w.essai_avertissement_le, w.essai_prolonge_le, w.essai_prolongation_jours,
           w.essai_fin_motif, w.subscription_status, r.adresse_ip, r.appareil,
           r.signalement, r.signalement_examine_le, r.cree_le, r.supprime_le
    FROM essais_registre r
    LEFT JOIN workspaces w ON w.id = r.workspace_id
    ORDER BY r.cree_le DESC
$$;

-- Cycle planifie de l'essai (RG-ESS-10) : seul chemin hors workspace (pas
-- d'utilisateur). Chaque essai est ensuite traite dans son workspace.
CREATE OR REPLACE FUNCTION essai_workspaces_a_traiter()
RETURNS TABLE (id uuid)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT w.id FROM workspaces w
    WHERE w.trial_status IN ('TRIAL_ACTIVE', 'TRIAL_EXPIRED')
    ORDER BY w.created_at
$$;

-- Purge des demandes perimees ou deja transformees en essai (donnees
-- personnelles conservees le temps de la verification seulement).
CREATE OR REPLACE FUNCTION essai_purger_demandes(p_limite timestamptz)
RETURNS integer
LANGUAGE sql VOLATILE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    WITH supprimees AS (
        DELETE FROM essai_demandes
        WHERE expire_le < p_limite OR workspace_id IS NOT NULL
        RETURNING 1
    )
    SELECT COUNT(*)::integer FROM supprimees
$$;

-- --------------------------------------------------------------- droits

REVOKE EXECUTE ON FUNCTION essai_identite_deja_utilisee(text, text, text, text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION essai_origines_communes(text, text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION admin_liste_essais() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION essai_workspaces_a_traiter() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION essai_purger_demandes(timestamptz) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION essai_identite_deja_utilisee(text, text, text, text) TO jurika_app;
GRANT EXECUTE ON FUNCTION essai_origines_communes(text, text) TO jurika_app;
GRANT EXECUTE ON FUNCTION admin_liste_essais() TO jurika_app;
GRANT EXECUTE ON FUNCTION essai_workspaces_a_traiter() TO jurika_app;
GRANT EXECUTE ON FUNCTION essai_purger_demandes(timestamptz) TO jurika_app;

REVOKE ALL ON essai_demandes FROM jurika_app;
GRANT SELECT, INSERT, UPDATE ON essai_demandes TO jurika_app;

REVOKE ALL ON essais_registre FROM jurika_app;
GRANT SELECT, INSERT, UPDATE ON essais_registre TO jurika_app;

REVOKE ALL ON essai_conditions_acceptations FROM jurika_app;
GRANT SELECT, INSERT ON essai_conditions_acceptations TO jurika_app;
