-- =====================================================================
-- JURIKA V19 -- Les CINQ statuts de ticket du guide du cabinet
--
-- Source : specs/creation-2026-09/GUIDE_CREATION_ENTREPRISE_MAROC_SARL_v2.xlsx,
--          onglet « 2. Workflow ticket ».
--
--   1 CREATION_TICKET       Creation du ticket          etapes 1 a 3
--   2 GENERATION_DOCUMENTS  Generation des documents    etapes 4 a 12
--   3 DEROULEMENT_DEMARCHE  Deroulement de la demarche  etapes 13 a 33
--   4 CLOTURE_DOSSIER       Cloture de dossier          etapes 34 a 36
--   5 ANNULE                Ticket annule               sortie laterale
--
-- CORRESPONDANCE AVEC LES 4 ANCIENS STATUTS
-- ------------------------------------------
--   NOUVEAU  -> CREATION_TICKET       (le dossier vient d'etre ouvert)
--   EN_COURS -> GENERATION_DOCUMENTS  (premier statut « en cours » ; on ne
--               PRESUME PAS que les actes sont deja valides par le client, ce
--               qu'exigerait DEROULEMENT_DEMARCHE)
--   CLOTURE  -> CLOTURE_DOSSIER
--   ANNULE   -> ANNULE                (inchange : reduit fortement la surface
--               de reprise dans les services aval)
--
-- REGLE PROJET : `validate-on-migrate: false` rend MUETTE toute edition d'une
-- migration deja appliquee. Le CHECK de V3 n'est donc PAS edite : il est
-- remplace ici, par une migration additive et idempotente.
--
-- ORDRE DES OPERATIONS -- le CHECK est elargi AVANT l'UPDATE, sinon l'UPDATE
-- echouerait sur la contrainte encore en vigueur.
-- =====================================================================

-- 1. Elargir la colonne : GENERATION_DOCUMENTS et DEROULEMENT_DEMARCHE font
--    exactement 20 caracteres, soit la limite stricte de VARCHAR(20). On passe
--    a 30 pour laisser une marge aux statuts des autres workflows.
ALTER TABLE tickets ALTER COLUMN statut TYPE VARCHAR(30);

-- 2. CHECK transitoire : ancien vocabulaire ET nouveau, pour que l'UPDATE passe.
ALTER TABLE tickets DROP CONSTRAINT IF EXISTS tickets_statut_check;
ALTER TABLE tickets
    ADD CONSTRAINT tickets_statut_check
    CHECK (statut IN ('NOUVEAU','EN_COURS','CLOTURE','ANNULE',
                      'CREATION_TICKET','GENERATION_DOCUMENTS',
                      'DEROULEMENT_DEMARCHE','CLOTURE_DOSSIER'));

-- 3. Migration des donnees existantes.
UPDATE tickets SET statut = 'CREATION_TICKET'      WHERE statut = 'NOUVEAU';
UPDATE tickets SET statut = 'GENERATION_DOCUMENTS' WHERE statut = 'EN_COURS';
UPDATE tickets SET statut = 'CLOTURE_DOSSIER'      WHERE statut = 'CLOTURE';

-- 4. Les commentaires de transition portent l'ancien vocabulaire dans leur
--    metadata JSONB {"from": ..., "to": ...}. On le reecrit pour que l'historique
--    reste lisible et que TicketController (qui teste from=ANNULE/to=EN_COURS
--    pour detecter une reprise) continue de dire vrai.
UPDATE ticket_comments
SET metadata = jsonb_set(
        jsonb_set(metadata, '{from}',
                  to_jsonb(CASE metadata ->> 'from'
                               WHEN 'NOUVEAU'  THEN 'CREATION_TICKET'
                               WHEN 'EN_COURS' THEN 'GENERATION_DOCUMENTS'
                               WHEN 'CLOTURE'  THEN 'CLOTURE_DOSSIER'
                               ELSE metadata ->> 'from' END)),
        '{to}',
        to_jsonb(CASE metadata ->> 'to'
                     WHEN 'NOUVEAU'  THEN 'CREATION_TICKET'
                     WHEN 'EN_COURS' THEN 'GENERATION_DOCUMENTS'
                     WHEN 'CLOTURE'  THEN 'CLOTURE_DOSSIER'
                     ELSE metadata ->> 'to' END))
WHERE metadata ? 'from' AND metadata ? 'to'
  AND (metadata ->> 'from' IN ('NOUVEAU','EN_COURS','CLOTURE')
    OR metadata ->> 'to'   IN ('NOUVEAU','EN_COURS','CLOTURE'));

-- 5. CHECK definitif : les cinq statuts, et eux seuls.
ALTER TABLE tickets DROP CONSTRAINT IF EXISTS tickets_statut_check;
ALTER TABLE tickets
    ADD CONSTRAINT tickets_statut_check
    CHECK (statut IN ('CREATION_TICKET','GENERATION_DOCUMENTS',
                      'DEROULEMENT_DEMARCHE','CLOTURE_DOSSIER','ANNULE'));

ALTER TABLE tickets ALTER COLUMN statut SET DEFAULT 'CREATION_TICKET';

COMMENT ON COLUMN tickets.statut IS
    'Les 5 statuts du guide cabinet v2 (onglet 2). ANNULE est une sortie laterale '
    'accessible depuis n''importe quel statut, y compris CLOTURE_DOSSIER.';
