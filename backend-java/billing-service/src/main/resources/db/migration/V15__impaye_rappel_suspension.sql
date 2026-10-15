-- =====================================================================
-- JURIKA billing V15 -- Lot L7, etape E8 : echeance impayee, rappel,
-- suspension (article 4.4 du contrat d'abonnement), puis archivage a
-- l'arret du forfait (articles 11 et 12.2).
--
-- Article 4.4 : "en l'absence de paiement 15 jours apres l'echeance et apres
-- un rappel reste sans effet, l'acces au workspace est suspendu. Aucune
-- donnee n'est supprimee ; l'acces est retabli des le paiement constate."
--
-- 1. subscriptions (l'abonnement reste ACTIF pendant le retard et la
--    suspension : le forfait n'est pas arrete, la suspension n'est pas un
--    archivage) :
--      rappel_impaye_echeance  echeance dont le rappel a ete ENVOYE (un seul
--                              rappel par echeance) ;
--      rappel_impaye_envoye_le date de cet envoi ;
--      suspendu_le             suspension en cours (NULL : acces ouvert) ;
--      resiliation_effet       date d'effet d'une resiliation enregistree
--                              (RG-CTR-08) : l'archivage a lieu a cette date,
--                              meme apres la periode payee (avant ce lot, il
--                              avait lieu a la fin de la periode payee).
-- 2. impaye_journal : chaque fait de la sequence (rappel envoye ou en echec,
--    suspension faite, differee faute de rappel ou en echec, retablissement).
--    Rien n'est avale : un envoi rate se lit ici (motif 9). Ecrit une fois,
--    jamais modifie ni supprime (declencheur ; role applicatif : SELECT et
--    INSERT seulement, REVOKE explicite). Sans cle etrangere vers
--    subscriptions : la trace ne depend d'aucune autre ligne.
--
-- Isolation : RLS par workspace (app.current_workspace_id) ET filtrage
-- applicatif explicite ; la tache quotidienne et le super-admin posent, dans
-- leur transaction, app.facturation_portee = 'globale'.
-- Colonnes et table ajoutees, aucune donnee existante modifiee.
-- =====================================================================

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS rappel_impaye_echeance  DATE,
    ADD COLUMN IF NOT EXISTS rappel_impaye_envoye_le TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS suspendu_le             TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS resiliation_effet       DATE;

COMMENT ON COLUMN subscriptions.rappel_impaye_echeance IS
    'Lot L7 (V15) -- echeance impayee dont le rappel a ete envoye (article 4.4) ; un seul rappel par echeance.';
COMMENT ON COLUMN subscriptions.suspendu_le IS
    'Lot L7 (V15) -- acces suspendu pour impaye (article 4.4), rien n''est supprime ; NULL apres le paiement constate.';
COMMENT ON COLUMN subscriptions.resiliation_effet IS
    'Lot L7 (V15) -- date d''effet de la resiliation enregistree (RG-CTR-08, article 12) : archivage a cette date.';

CREATE TABLE IF NOT EXISTS impaye_journal (
    id             BIGSERIAL     PRIMARY KEY,
    workspace_id   UUID          NOT NULL,
    abonnement_id  BIGINT        NOT NULL,
    echeance       DATE          NOT NULL,
    evenement      VARCHAR(30)   NOT NULL,
    destinataire   VARCHAR(320),
    detail         TEXT,
    cree_le        TIMESTAMPTZ   NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_impaye_journal_evenement CHECK (evenement IN (
        'RAPPEL_ENVOYE', 'RAPPEL_ECHEC', 'SUSPENSION', 'SUSPENSION_ECHEC',
        'SUSPENSION_SANS_RAPPEL', 'RETABLISSEMENT'))
);
CREATE INDEX IF NOT EXISTS idx_impaye_journal_cabinet ON impaye_journal (workspace_id, cree_le DESC);

COMMENT ON TABLE impaye_journal IS
    'Lot L7 (V15) -- sequence d''une echeance impayee (article 4.4) : rappel, suspension, retablissement. Inalterable.';

CREATE OR REPLACE FUNCTION impaye_journal_inalterable()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Journal des impayes inalterable : % refuse', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END
$$;

DROP TRIGGER IF EXISTS trg_impaye_journal_inalterable ON impaye_journal;
CREATE TRIGGER trg_impaye_journal_inalterable
    BEFORE UPDATE OR DELETE ON impaye_journal
    FOR EACH ROW EXECUTE FUNCTION impaye_journal_inalterable();

ALTER TABLE impaye_journal ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS impaye_journal_isolation ON impaye_journal;
CREATE POLICY impaye_journal_isolation ON impaye_journal FOR ALL
    USING (current_setting('app.facturation_portee', true) = 'globale'
           OR workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid)
    WITH CHECK (current_setting('app.facturation_portee', true) = 'globale'
           OR workspace_id = NULLIF(current_setting('app.current_workspace_id', true), '')::uuid);

REVOKE ALL ON impaye_journal FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        REVOKE ALL ON impaye_journal FROM jurika_app;
        GRANT SELECT, INSERT ON impaye_journal TO jurika_app;
        GRANT USAGE, SELECT ON SEQUENCE impaye_journal_id_seq TO jurika_app;
    END IF;
END
$$;
