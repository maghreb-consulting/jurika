-- =====================================================================
-- JURIKA V28 (ticket-service) -- Lot L1, etape E3 : responsable obligatoire
--
-- RG-DOS-01 : chaque dossier a UN employe responsable. RG-TKT-08 : les tickets
-- suivent leur dossier ; tout changement de responsable est trace (auteur, date,
-- ancien et nouveau responsable). RG-DOS-03 : reaffectation d'office tracee.
--
-- 1. Table des reaffectations : historique de tout changement de responsable
--    (ACCEPTEE = transfert accepte, FORCEE = reaffectation d'office par le
--    superviseur, RATTRAPAGE = attribution par la presente migration).
-- 2. Rattrapage TRACE des dossiers sans responsable (releve du Z440 le 2026-10-09 :
--    9 dossiers sur 13 ; V11/V12 avaient deja rattrape, mais trois chemins
--    d'insertion n'ecrivaient pas responsable_id), dans cet ordre :
--      a. l'unique EMPLOYE assigne aux tickets du dossier ;
--      b. sinon, le createur EMPLOYE du premier ticket du dossier ;
--      c. sinon, l'EMPLOYE actif le plus ancien du workspace ;
--      d. sinon : ECHEC explicite de la migration (dossier nomme). Aucune valeur
--         n'est inventee en silence ; un superviseur n'est jamais responsable
--         (il observe, CDC 3.2).
-- 3. Un responsable qui n'est pas un EMPLOYE (superviseur attribue par V12) est
--    remplace par la meme regle, et trace.
-- 4. tickets.assigne_id aligne sur le responsable du dossier (RG-TKT-08).
-- 5. Contraintes : responsable_id NOT NULL ; cles etrangeres en RESTRICT (un compte
--    n'est jamais supprime, RG-CPT-03 ; un dossier non plus). tickets.dossier_id reste
--    nullable (tickets SUCCURSALE_ETR, voir plus bas).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Historique des changements de responsable
-- ---------------------------------------------------------------------
CREATE TABLE dossier_reaffectations (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id           UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    dossier_id             UUID NOT NULL REFERENCES entreprise_dossiers(id) ON DELETE RESTRICT,
    ancien_responsable_id  UUID REFERENCES users(id) ON DELETE RESTRICT,
    nouveau_responsable_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    nature                 VARCHAR(20) NOT NULL
                           CHECK (nature IN ('ACCEPTEE', 'FORCEE', 'RATTRAPAGE')),
    auteur_id              UUID REFERENCES users(id) ON DELETE RESTRICT,
    transfert_id           UUID REFERENCES dossier_transfert_requests(id) ON DELETE RESTRICT,
    motif                  TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_reaffectation_auteur CHECK (nature = 'RATTRAPAGE' OR auteur_id IS NOT NULL),
    CONSTRAINT ck_reaffectation_transfert CHECK (nature <> 'ACCEPTEE' OR transfert_id IS NOT NULL)
);
CREATE INDEX idx_reaffectations_dossier ON dossier_reaffectations (workspace_id, dossier_id, created_at);

ALTER TABLE dossier_reaffectations ENABLE ROW LEVEL SECURITY;
CREATE POLICY reaffectations_isolation ON dossier_reaffectations FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

-- ---------------------------------------------------------------------
-- 2 et 3. Rattrapage trace
-- ---------------------------------------------------------------------
CREATE TEMPORARY TABLE l1_rattrapage ON COMMIT DROP AS
SELECT d.id AS dossier_id,
       d.workspace_id,
       d.responsable_id AS ancien,
       COALESCE(
           -- a. l'unique EMPLOYE assigne aux tickets du dossier
           (SELECT MIN(t.assigne_id::text)::uuid
              FROM tickets t JOIN users u ON u.id = t.assigne_id
             WHERE t.dossier_id = d.id AND u.role = 'EMPLOYE' AND u.workspace_id = d.workspace_id
            HAVING COUNT(DISTINCT t.assigne_id) = 1),
           -- b. le createur EMPLOYE du premier ticket
           (SELECT t.cree_par_id
              FROM tickets t JOIN users u ON u.id = t.cree_par_id
             WHERE t.dossier_id = d.id AND u.role = 'EMPLOYE' AND u.workspace_id = d.workspace_id
             ORDER BY t.created_at, t.id
             LIMIT 1),
           -- c. l'EMPLOYE actif le plus ancien du workspace
           (SELECT u.id
              FROM users u
             WHERE u.workspace_id = d.workspace_id AND u.role = 'EMPLOYE' AND u.is_active
             ORDER BY u.created_at, u.id
             LIMIT 1)
       ) AS nouveau
  FROM entreprise_dossiers d
 WHERE d.responsable_id IS NULL
    OR NOT EXISTS (SELECT 1 FROM users u
                    WHERE u.id = d.responsable_id AND u.role = 'EMPLOYE');

DO $$
DECLARE
    orphelins text;
BEGIN
    SELECT string_agg(dossier_id::text || ' (workspace ' || workspace_id::text || ')', ', ')
      INTO orphelins
      FROM l1_rattrapage WHERE nouveau IS NULL;
    IF orphelins IS NOT NULL THEN
        RAISE EXCEPTION 'V28 : aucun employe ne peut etre designe responsable de : %. '
            'Creer un compte EMPLOYE dans ce workspace, puis relancer.', orphelins;
    END IF;
END
$$;

INSERT INTO dossier_reaffectations
    (workspace_id, dossier_id, ancien_responsable_id, nouveau_responsable_id, nature, motif)
SELECT workspace_id, dossier_id, ancien, nouveau, 'RATTRAPAGE',
       CASE WHEN ancien IS NULL
            THEN 'Migration V28 (lot L1) : dossier sans responsable'
            ELSE 'Migration V28 (lot L1) : responsable non EMPLOYE remplace' END
  FROM l1_rattrapage;

UPDATE entreprise_dossiers d
   SET responsable_id = r.nouveau, updated_at = NOW()
  FROM l1_rattrapage r
 WHERE d.id = r.dossier_id;

-- ---------------------------------------------------------------------
-- 4. Les tickets suivent leur dossier
-- ---------------------------------------------------------------------
UPDATE tickets t
   SET assigne_id = d.responsable_id
  FROM entreprise_dossiers d
 WHERE d.id = t.dossier_id
   AND t.assigne_id IS DISTINCT FROM d.responsable_id;

-- ---------------------------------------------------------------------
-- 5. Contraintes
-- ---------------------------------------------------------------------
ALTER TABLE entreprise_dossiers ALTER COLUMN responsable_id SET NOT NULL;
ALTER TABLE entreprise_dossiers DROP CONSTRAINT IF EXISTS entreprise_dossiers_responsable_id_fkey;
ALTER TABLE entreprise_dossiers ADD CONSTRAINT entreprise_dossiers_responsable_id_fkey
    FOREIGN KEY (responsable_id) REFERENCES users(id) ON DELETE RESTRICT;

-- tickets.dossier_id reste NULLABLE : un ticket SUCCURSALE_ETR nait sans dossier (le
-- dossier de la societe mere etrangere n'est cree qu'a la validation de l'etape 1,
-- WorkflowUseCases#withDossierMereEtrangere). Seule la cle etrangere passe en RESTRICT.
ALTER TABLE tickets DROP CONSTRAINT IF EXISTS tickets_dossier_id_fkey;
ALTER TABLE tickets ADD CONSTRAINT tickets_dossier_id_fkey
    FOREIGN KEY (dossier_id) REFERENCES entreprise_dossiers(id) ON DELETE RESTRICT;
