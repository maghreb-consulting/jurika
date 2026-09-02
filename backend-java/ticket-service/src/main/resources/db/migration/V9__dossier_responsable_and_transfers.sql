-- =====================================================================
-- JURIKA V9 (ticket-service) — Transfert de dossier entre employes
--
-- Modele decide 2026-06-25 :
--  1) "responsable_id" durable sur entreprise_dossiers = source de verite
--     de "qui gere la societe" (au-dela de l'assignation des tickets).
--  2) Table dossier_transfert_requests = cycle de vie demande/acceptation
--     (VOIE A entre employes) + trace des transferts directs superviseur
--     (VOIE B).
--
-- L'application effective d'un transfert (responsable_id + reassignation
-- des tickets OUVERTS + audit DOSSIER_TRANSFERE) reste dans ticket-service
-- (DossierTransferService), seul proprietaire transactionnel de
-- entreprise_dossiers + tickets.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Colonne responsable_id (owner durable du dossier)
-- ---------------------------------------------------------------------
ALTER TABLE entreprise_dossiers
    ADD COLUMN IF NOT EXISTS responsable_id UUID
        REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_dossiers_responsable
    ON entreprise_dossiers (workspace_id, responsable_id);

-- Backfill : pour ne pas laisser les dossiers existants sans responsable,
-- on retient, par dossier, l'assigne du ticket OUVERT (NOUVEAU/EN_COURS)
-- le plus recent ; a defaut l'assigne du ticket le plus recent tout statut ;
-- a defaut le createur (cree_par_id). COALESCE couvre le ticket non assigne.
UPDATE entreprise_dossiers d
   SET responsable_id = sub.responsable
  FROM (
        SELECT DISTINCT ON (t.dossier_id)
               t.dossier_id,
               COALESCE(t.assigne_id, t.cree_par_id) AS responsable
          FROM tickets t
         WHERE t.dossier_id IS NOT NULL
         ORDER BY t.dossier_id,
                  CASE WHEN t.statut IN ('NOUVEAU','EN_COURS') THEN 0 ELSE 1 END,
                  t.created_at DESC
       ) sub
 WHERE d.id = sub.dossier_id
   AND d.responsable_id IS NULL;

-- ---------------------------------------------------------------------
-- 2. Table dossier_transfert_requests (demande/acceptation + direct)
-- ---------------------------------------------------------------------
CREATE TABLE dossier_transfert_requests (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id  UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    dossier_id    UUID        NOT NULL REFERENCES entreprise_dossiers(id) ON DELETE CASCADE,
    from_user_id  UUID        NOT NULL REFERENCES users(id),
    to_user_id    UUID        NOT NULL REFERENCES users(id),
    statut        VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE'
                  CHECK (statut IN ('EN_ATTENTE','ACCEPTE','REFUSE','ANNULE')),
    -- DIRECT = transfert immediat superviseur (VOIE B) : la ligne nait deja ACCEPTE.
    direct        BOOLEAN     NOT NULL DEFAULT FALSE,
    motif         TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_at    TIMESTAMPTZ
);
CREATE INDEX idx_transfer_req_inbox  ON dossier_transfert_requests (workspace_id, to_user_id, statut);
CREATE INDEX idx_transfer_req_outbox ON dossier_transfert_requests (workspace_id, from_user_id, statut);
CREATE INDEX idx_transfer_req_dossier ON dossier_transfert_requests (workspace_id, dossier_id, statut);

-- Au plus une demande EN_ATTENTE par dossier (garde-fou anti-doublon).
CREATE UNIQUE INDEX uq_transfer_req_pending_dossier
    ON dossier_transfert_requests (dossier_id)
    WHERE statut = 'EN_ATTENTE';

-- ---------------------------------------------------------------------
-- 3. RLS multi-tenant (meme pattern que V3)
-- ---------------------------------------------------------------------
ALTER TABLE dossier_transfert_requests ENABLE ROW LEVEL SECURITY;

CREATE POLICY transfer_req_isolation ON dossier_transfert_requests FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);
