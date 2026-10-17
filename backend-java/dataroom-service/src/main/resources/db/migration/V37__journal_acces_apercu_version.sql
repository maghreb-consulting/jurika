-- =====================================================================
-- JURIKA V37 (dataroom-service) -- Lot L6b, etape E9 : l'apercu d'une
-- version d'un document est trace dans le journal d'acces du client.
--
-- JuridiqueController emet PREVIEW_VERSION (apercu d'une version, deux
-- routes) depuis le lot des versions, mais la contrainte CHECK posee par V21
-- ne l'admet pas : l'insertion est rejetee et ClientAccessLogDispatcher
-- n'ecrit qu'un avertissement (trace perdue sans bruit, motif 9). Releve par
-- AuditActionLabelsInventaireTest (jurika-common), qui compare les actions
-- emises a la derniere contrainte posee.
--
-- Le CHECK est repose en entier (sur-ensemble de V21) : une migration
-- appliquee ne se modifie jamais. Toute migration ulterieure qui repose ce
-- CHECK doit reprendre les huit actions. 'PREVIEW_VERSION' = 15 caracteres
-- (colonne VARCHAR(20)).
-- =====================================================================

ALTER TABLE dataroom_client_access_log
    DROP CONSTRAINT IF EXISTS dataroom_client_access_log_action_check;

ALTER TABLE dataroom_client_access_log
    ADD CONSTRAINT dataroom_client_access_log_action_check
    CHECK (action IN (
        'VIEW_DOSSIER','PREVIEW_DOC','PREVIEW_VERSION','DOWNLOAD_DOC','DOWNLOAD_VERSION',
        'PRINT_DOC','DEPOT_DOC','DEMANDE'));
