-- =====================================================================
-- JURIKA V22 -- Lot AG : « Requetes au client » (communication employe -> client)
--
-- On REUTILISE dataroom_demandes_client pour porter les DEUX directions :
--   - CLIENT_TO_EMPLOYE : demandes existantes (client -> employe) [inchangees]
--   - EMPLOYE_TO_CLIENT : requetes de l'employe au client (pilotees par le client)
--
-- Machine a etats EMPLOYE_TO_CLIENT (cloture en 2 etapes) :
--   OUVERTE --(client repond/fournit)--> REPONDUE
--     REPONDUE --(employe valide)--------> CLOTUREE
--     REPONDUE --(employe demande +)------> A_COMPLETER --(client re-repond)--> REPONDUE
--
-- ALTER idempotents (ADD COLUMN IF NOT EXISTS + recreation des CHECK).
-- =====================================================================

ALTER TABLE dataroom_demandes_client
    ADD COLUMN IF NOT EXISTS direction VARCHAR(20) NOT NULL DEFAULT 'CLIENT_TO_EMPLOYE';
ALTER TABLE dataroom_demandes_client
    ADD COLUMN IF NOT EXISTS type_requete VARCHAR(20);   -- PIECE | INFO | SIGNATURE (EMPLOYE_TO_CLIENT)
ALTER TABLE dataroom_demandes_client
    ADD COLUMN IF NOT EXISTS repondu_at TIMESTAMPTZ;
ALTER TABLE dataroom_demandes_client
    ADD COLUMN IF NOT EXISTS cloture_at TIMESTAMPTZ;
ALTER TABLE dataroom_demandes_client
    ADD COLUMN IF NOT EXISTS note_client TEXT;

-- direction : valeurs bornees
ALTER TABLE dataroom_demandes_client DROP CONSTRAINT IF EXISTS chk_demande_direction;
ALTER TABLE dataroom_demandes_client
    ADD CONSTRAINT chk_demande_direction
    CHECK (direction IN ('CLIENT_TO_EMPLOYE','EMPLOYE_TO_CLIENT'));

-- type_requete : null (CLIENT_TO_EMPLOYE) ou une des 3 valeurs (EMPLOYE_TO_CLIENT)
ALTER TABLE dataroom_demandes_client DROP CONSTRAINT IF EXISTS chk_demande_type_requete;
ALTER TABLE dataroom_demandes_client
    ADD CONSTRAINT chk_demande_type_requete
    CHECK (type_requete IS NULL OR type_requete IN ('PIECE','INFO','SIGNATURE'));

-- statut : etendre l'ancien CHECK (garder les 3 existants pour la retro-compat)
ALTER TABLE dataroom_demandes_client DROP CONSTRAINT IF EXISTS dataroom_demandes_client_statut_check;
ALTER TABLE dataroom_demandes_client
    ADD CONSTRAINT dataroom_demandes_client_statut_check
    CHECK (statut IN ('NON_TRAITEE','EN_COURS','TRAITEE','OUVERTE','REPONDUE','CLOTUREE','A_COMPLETER'));

CREATE INDEX IF NOT EXISTS idx_demandes_direction
    ON dataroom_demandes_client (workspace_id, dossier_id, direction, statut);
