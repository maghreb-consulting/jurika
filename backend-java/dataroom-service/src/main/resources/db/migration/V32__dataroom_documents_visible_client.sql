-- =====================================================================
-- JURIKA V32 — « Visible pour le client », sur le DOCUMENT
--
-- CE QUI EXISTAIT
-- Rien de tel. `dataroom_settings` porte trois reglages — `perm_download`,
-- `perm_print`, `perm_depot` — et un `access_status`. Ce sont des droits
-- GLOBAUX sur un dossier : « ce client peut-il telecharger ? », jamais « ce
-- document-ci lui est-il montre ? ». Aucune requete de listage ne filtre par
-- document.
--
-- CE QUE LE LOT DEMANDE
-- Chaque document porte un indicateur « visible pour le client », regle au depot
-- depuis le panneau de cochage et modifiable ensuite depuis la Data Room. Ce
-- n'est donc pas un reglage du workflow mais une PROPRIETE DU DOCUMENT : elle se
-- porte ici, et s'expose des deux cotes.
--
-- ---------------------------------------------------------------------
-- LA VALEUR PAR DEFAUT : VISIBLE — et pourquoi
--
-- Deux raisons, l'une de fond, l'autre de migration.
--
-- 1. La Data Room EST le dossier du client. Les pieces que le parcours fait
--    televerser sont, par construction, les « justificatifs a obtenir et
--    archiver » de ses propres formalites : un recepisse de depot, une
--    attestation d'enregistrement, un modele J. Les masquer par defaut
--    rendrait son dossier silencieusement incomplet, et l'oubli le plus
--    probable — un employe qui ne pense pas a cocher « visible » — priverait le
--    client d'une piece a laquelle il a droit, sans que personne le sache.
--    L'oubli inverse, laisser visible une piece qu'on voulait garder, se voit :
--    le client la lit et en parle.
--
-- 2. Aucune regression silencieuse sur l'existant. Les documents deja deposes
--    sont aujourd'hui visibles du client des lors que le dossier est ACTIF.
--    Poser FALSE par defaut les ferait TOUS disparaitre de son espace a la
--    minute de la migration, sans qu'aucun employe ait rien decide.
--
-- L'EXCEPTION : `AUTRE`. « Un recepisse de depot n'a pas le meme statut qu'une
-- note interne » — et `AUTRE` est precisement le fourre-tout des documents dont
-- la nature n'est pas deductible, donc le seul endroit ou une note interne peut
-- atterrir. Un document de type `AUTRE` est donc MASQUE par defaut. Le reglage
-- reste un clic dans les deux sens, au depot comme depuis la Data Room.
--
-- La regle tient en une phrase : ce que le referentiel sait nommer appartient au
-- dossier du client ; ce qu'il ne sait pas nommer attend une decision.
-- =====================================================================

ALTER TABLE dataroom_documents
    ADD COLUMN IF NOT EXISTS visible_client BOOLEAN NOT NULL DEFAULT TRUE;

-- Le passe : tout ce qui est type reste visible, `AUTRE` passe masque. La
-- colonne `groupe` (V24) le dit deja pour les documents ranges ; on s'appuie sur
-- le TYPE, qui est NOT NULL depuis V5.
UPDATE dataroom_documents SET visible_client = FALSE WHERE document_type = 'AUTRE';

CREATE INDEX IF NOT EXISTS idx_dataroom_docs_visible
    ON dataroom_documents (workspace_id, dossier_id, visible_client)
    WHERE is_current;

COMMENT ON COLUMN dataroom_documents.visible_client IS
    'Le client voit-il ce document dans sa Data Room ? Regle au depot, modifiable ensuite '
    'depuis la Data Room. Defaut TRUE (le document appartient au dossier du client), sauf '
    'les documents de type AUTRE, dont la nature n''est pas deductible.';

-- ---------------------------------------------------------------------
-- Journal des changements de visibilite
--
-- Retirer une piece de la vue du client est une decision ; la rendre a nouveau
-- visible en est une autre. Les deux doivent se relire, et « qui a masque ce
-- recepisse, et quand ? » doit avoir une reponse.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dataroom_visibilite_evenements (
    id           UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    document_id  UUID        NOT NULL REFERENCES dataroom_documents(id) ON DELETE CASCADE,
    visible      BOOLEAN     NOT NULL,
    origine      VARCHAR(20) NOT NULL CHECK (origine IN ('DEPOT','DATAROOM','WORKFLOW')),
    acteur_id    UUID        REFERENCES users(id) ON DELETE SET NULL,
    survenu_le   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_dve_document
    ON dataroom_visibilite_evenements (document_id, survenu_le);

ALTER TABLE dataroom_visibilite_evenements ENABLE ROW LEVEL SECURITY;
CREATE POLICY dve_isolation ON dataroom_visibilite_evenements FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

COMMENT ON TABLE dataroom_visibilite_evenements IS
    'Qui a montre ou masque quel document, quand, et depuis ou (depot, Data Room, workflow).';
