-- =====================================================================
-- JURIKA V26 — Persistance des documents générés (lot 2, 2026-09-07)
--
-- PROBLÈME CORRIGÉ
-- Un acte généré à l'étape 7 d'un workflow n'existait que comme Blob en
-- mémoire React : `workflow_progress.data` ne portait que step1…step6. Quatre
-- symptômes rapportés par le cabinet — documents « disparus » au retour sur la
-- page, édition perdue par un simple Précédent/Suivant, régénération exigée,
-- confusion aperçu/édition — avaient cette unique cause. Rien n'était stocké
-- avant le clic « Valider et déposer en Dataroom ».
--
-- CHOIX : LE BROUILLON EST UN DOCUMENT DE LA DATA ROOM, PAS UN MÉCANISME À CÔTÉ
-- On aurait pu stocker le .docx en base64 dans `workflow_progress.data`, ou
-- créer une table parallèle. Ni l'un ni l'autre :
--   * `data` est relu et réécrit ENTIÈREMENT à chaque sauvegarde d'étape ; y
--     loger du binaire ferait grossir chaque aller-retour, sans versionnement,
--     sans purge, sans téléchargement, sans audit ;
--   * une table parallèle dupliquerait le stockage objet, le téléchargement,
--     l'aperçu, l'export ZIP et la traçabilité qui existent déjà ici.
-- Le brouillon réutilise donc la ligne `dataroom_documents` et son objet MinIO.
-- Il devient un document validé par simple bascule de drapeaux, et emprunte
-- alors le versionnement juridique existant (`replaceAsNewVersion` et l'index
-- unique par emplacement) au lieu d'un chemin de dépôt distinct.
--
-- CE QUI PROTÈGE LE DOSSIER JURIDIQUE D'UN BROUILLON
--   1. `is_current = false` : un brouillon reste HORS de l'index unique partiel
--      `ux_dataroom_documents_courant_par_slot`. Il n'occupe donc jamais
--      l'emplacement du document en vigueur, et le valider ne pousse pas un
--      brouillon dans l'historique juridique.
--   2. CHECK `NOT (brouillon AND is_current)` : la base refuse la combinaison,
--      une régression du code échoue à l'INSERT au lieu de faire apparaître un
--      acte non validé parmi les documents en vigueur.
--   3. CHECK `brouillon => ticket_id IS NOT NULL` : un brouillon appartient à
--      une opération. Sans ticket il rejoindrait le regroupement « Hors ticket »
--      du dossier juridique, ce qui est exactement ce qu'on veut éviter.
--   4. Index unique `(workspace_id, ticket_id, document_type, title) WHERE
--      brouillon` : une régénération REMPLACE le brouillon, elle ne l'empile
--      pas. Le cabinet ne verra jamais quatre versions de travail du même acte.
--
-- Les lectures (documents en vigueur, dossier par ticket, lignage des versions,
-- recherche plein texte) excluent explicitement `brouillon` — cf.
-- DocumentJpaRepository.
-- =====================================================================

ALTER TABLE dataroom_documents
    ADD COLUMN IF NOT EXISTS brouillon BOOLEAN NOT NULL DEFAULT FALSE;

-- Un brouillon n'est jamais « en vigueur ».
ALTER TABLE dataroom_documents
    DROP CONSTRAINT IF EXISTS ck_dataroom_documents_brouillon_non_courant;
ALTER TABLE dataroom_documents
    ADD CONSTRAINT ck_dataroom_documents_brouillon_non_courant
    CHECK (NOT (brouillon AND is_current));

-- Un brouillon appartient à une opération identifiée.
ALTER TABLE dataroom_documents
    DROP CONSTRAINT IF EXISTS ck_dataroom_documents_brouillon_ticket;
ALTER TABLE dataroom_documents
    ADD CONSTRAINT ck_dataroom_documents_brouillon_ticket
    CHECK (NOT brouillon OR ticket_id IS NOT NULL);

-- Un seul brouillon par (opération, type, titre) : régénérer remplace.
CREATE UNIQUE INDEX IF NOT EXISTS ux_dataroom_documents_brouillon_par_slot
    ON dataroom_documents (workspace_id, ticket_id, document_type, title)
    WHERE brouillon;

-- Lecture des brouillons d'une opération au montage de l'étape 7.
CREATE INDEX IF NOT EXISTS idx_dataroom_docs_brouillon_ticket
    ON dataroom_documents (workspace_id, ticket_id)
    WHERE brouillon;

COMMENT ON COLUMN dataroom_documents.brouillon IS
    'Document généré par un workflow et NON encore validé par l''employé. '
    'Exclu du dossier juridique, des documents en vigueur, du lignage des '
    'versions et de la recherche. Devient un document normal à la validation.';
