-- =====================================================================
-- JURIKA V40 -- Lot L5 (E6) : etat des debours (cahier des charges 10.1).
--
-- RG-DEB-01 : une ligne par frais, typee parmi exactement sept types :
--   DROITS_ENREGISTREMENT  droits d'enregistrement
--   FRAIS_GREFFE           frais de greffe
--   ANNONCE_LEGALE         annonce legale
--   TIMBRES                timbres
--   LEGALISATION           legalisation
--   FRAIS_ORGANISME        frais d'organisme
--   AUTRE                  autre
-- La categorie de V3 portait huit autres valeurs. Reprise SANS PERTE :
--   * equivalences exactes : FRAIS_ENREGISTREMENT -> DROITS_ENREGISTREMENT,
--     PUBLICATION_JAL et PUBLICATION_BO -> ANNONCE_LEGALE ;
--   * toute autre valeur (FRAIS_TRIBUNAL, FRAIS_NOTARIE, HONORAIRES, TRANSPORT,
--     ou inconnue) -> AUTRE ; le libelle de la ligne n'est pas touche.
--   Dans tous les cas ou la valeur change, l'ancienne est conservee dans
--   categorie_reprise (l'employe peut reclasser la ligne en connaissance de cause).
--
-- La piece justificative est deposee en Data Room du dossier (dataroom_documents,
-- table du dataroom-service) : piece_document_id en garde l'identifiant. Pas de
-- cle etrangere : la Data Room gere seule la vie de ses documents (versions,
-- suppression) ; le ticket-service verifie a l'ecriture que le document existe
-- dans le meme cabinet et le meme dossier.
--
-- La table garde sa RLS (V3, debours_isolation) ; aucune nouvelle table.
-- =====================================================================

ALTER TABLE ticket_debours ADD COLUMN IF NOT EXISTS categorie_reprise VARCHAR(40);
ALTER TABLE ticket_debours ADD COLUMN IF NOT EXISTS piece_document_id UUID;

ALTER TABLE ticket_debours DROP CONSTRAINT IF EXISTS ticket_debours_categorie_check;

UPDATE ticket_debours
   SET categorie_reprise = categorie,
       categorie = CASE categorie
                       WHEN 'FRAIS_ENREGISTREMENT' THEN 'DROITS_ENREGISTREMENT'
                       WHEN 'PUBLICATION_JAL'      THEN 'ANNONCE_LEGALE'
                       WHEN 'PUBLICATION_BO'       THEN 'ANNONCE_LEGALE'
                       ELSE 'AUTRE'
                   END
 WHERE categorie NOT IN ('DROITS_ENREGISTREMENT', 'FRAIS_GREFFE', 'ANNONCE_LEGALE',
                         'TIMBRES', 'LEGALISATION', 'FRAIS_ORGANISME', 'AUTRE');

ALTER TABLE ticket_debours ADD CONSTRAINT ticket_debours_categorie_check
    CHECK (categorie IN ('DROITS_ENREGISTREMENT', 'FRAIS_GREFFE', 'ANNONCE_LEGALE',
                         'TIMBRES', 'LEGALISATION', 'FRAIS_ORGANISME', 'AUTRE'));

CREATE INDEX IF NOT EXISTS idx_debours_piece ON ticket_debours (workspace_id, piece_document_id)
    WHERE piece_document_id IS NOT NULL;

COMMENT ON COLUMN ticket_debours.categorie_reprise IS
    'Lot L5 (V40) : categorie anterieure, conservee quand la reprise l''a changee.';
COMMENT ON COLUMN ticket_debours.piece_document_id IS
    'Lot L5 (V40) : piece justificative deposee en Data Room (dataroom_documents.id).';
