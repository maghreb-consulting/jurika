-- =====================================================================
-- JURIKA V7 -- RG-DR24 : Migration archives V1 vers ticket_document_snapshots
-- Reference : docs/v2/Regles_de_Gestion_V2.md RG-DR24
--
-- La V1 utilisait une table "documents_archive" pour stocker les anciennes versions.
-- La V2 abandonne cette table : les anciennes versions sont accessibles via
-- l'historique des tickets (ticket_document_snapshots, kind=REPLACED).
--
-- Cette migration est NO-OP si la table V1 n'existe pas (cas du PFE en developpement).
-- Pour les installations qui auraient des donnees V1, le bloc DO migre chaque ligne.
-- =====================================================================

DO $$
DECLARE
    archive_table_exists BOOLEAN;
    migrated_count       INTEGER := 0;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'documents_archive'
    ) INTO archive_table_exists;

    IF NOT archive_table_exists THEN
        RAISE NOTICE 'RG-DR24 : Aucune table documents_archive V1 -- migration NO-OP';
        RETURN;
    END IF;

    -- Pour chaque archive V1 ayant un ticket_source_id renseigne, creer le snapshot
    INSERT INTO ticket_document_snapshots (workspace_id, ticket_id, document_id, snapshot_kind, captured_at)
    SELECT a.workspace_id, a.ticket_source_id, a.document_id, 'REPLACED', a.archived_at
    FROM documents_archive a
    WHERE a.ticket_source_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM ticket_document_snapshots s
          WHERE s.ticket_id = a.ticket_source_id AND s.document_id = a.document_id
      );

    GET DIAGNOSTICS migrated_count = ROW_COUNT;
    RAISE NOTICE 'RG-DR24 : % archives V1 migrees vers ticket_document_snapshots', migrated_count;
END $$;
