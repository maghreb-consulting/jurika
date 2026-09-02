-- =====================================================================
-- JURIKA V21 -- Lot X : elargir les actions tracables du journal d'acces
--                       client (dataroom_client_access_log.action).
--
-- V11 limitait le CHECK aux 4 actions initiales :
--   ('VIEW_DOSSIER','PREVIEW_DOC','DOWNLOAD_DOC','PRINT_DOC').
-- Depuis, le code emet aussi des actions qui n'etaient PAS dans le CHECK
-- et dont l'INSERT etait donc rejete en silence (best-effort catch dans
-- ClientAccessLogDispatcher) -> journal quasi vide -> compteur "Acces
-- client" bloque a 0 :
--   - DOWNLOAD_VERSION : telechargement d'une version specifique d'un
--                        document (JuridiqueController#downloadVersion).
--   - DEPOT_DOC        : import (depot) client (DataroomDepotService).
--   - DEMANDE          : creation d'une demande client (DemandesClientService).
--
-- On recree la contrainte CHECK pour couvrir les 7 actions. La contrainte
-- V11 etait inline (nom auto Postgres = <table>_<colonne>_check) ; on la
-- droppe via IF EXISTS puis on la recree NOMMEE (idempotent / lisible).
-- Toutes les valeurs restent <= VARCHAR(20) ('DOWNLOAD_VERSION' = 16).
-- =====================================================================

ALTER TABLE dataroom_client_access_log
    DROP CONSTRAINT IF EXISTS dataroom_client_access_log_action_check;

ALTER TABLE dataroom_client_access_log
    ADD CONSTRAINT dataroom_client_access_log_action_check
    CHECK (action IN (
        'VIEW_DOSSIER','PREVIEW_DOC','DOWNLOAD_DOC','DOWNLOAD_VERSION',
        'PRINT_DOC','DEPOT_DOC','DEMANDE'));
