-- =====================================================================
-- JURIKA V27 — Sessions d'édition WOPI + trace d'édition manuelle
-- Lot 3 (2026-09-07) — intégration de Collabora Online CODE
--
-- POURQUOI UNE TABLE PLUTÔT QUE REDIS
-- `dataroom-service` n'a aucune dépendance Redis. En ajouter une pour y loger
-- des jetons éphémères coûterait une configuration de plus (et, sous Windows,
-- le contournement Jedis déjà rencontré ailleurs dans ce projet). La table
-- rend deux services au lieu d'un : elle porte le jeton ET la trace « qui a
-- édité quel acte, quand, et combien de fois il a été enregistré » — que le
-- lot demande par ailleurs. Une seule chose à écrire, une seule à maintenir.
--
-- LE JETON N'EST PAS STOCKÉ EN CLAIR
-- Il voyage dans une URL (`access_token=`) et atterrit donc dans les journaux
-- de Collabora, dans l'historique du navigateur et dans les traces du reverse
-- proxy. On ne conserve que son empreinte SHA-256 : une fuite de la base ne
-- donne aucun accès. C'est la même raison qui interdit de réutiliser le JWT de
-- session comme jeton WOPI.
--
-- LA FENÊTRE DE GRÂCE
-- Collabora enregistre de façon ASYNCHRONE et peut appeler PutFile APRÈS la
-- fermeture de l'onglet. Un jeton révoqué à la fermeture ferait perdre la
-- dernière sauvegarde EN SILENCE — exactement le motif de perte muette qui
-- traverse ce projet. `grace_jusqua` autorise l'écriture au-delà de la
-- fermeture ; la lecture, elle, est coupée immédiatement.
-- =====================================================================

CREATE TABLE IF NOT EXISTS dataroom_wopi_sessions (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    -- Empreinte SHA-256 du jeton, en hexadécimal. Jamais le jeton lui-même.
    token_hash          CHAR(64)     NOT NULL,
    workspace_id        UUID         NOT NULL,
    document_id         UUID         NOT NULL,
    user_id             UUID         NOT NULL,
    -- Nom affiché dans l'éditeur (« Édité par … » chez Collabora).
    user_display_name   VARCHAR(200),
    -- Reflet du RBAC réel au moment de l'ouverture, pas d'un bouton masqué.
    can_write           BOOLEAN      NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ  NOT NULL,
    -- Renseigné à la fermeture explicite de l'éditeur par l'employé.
    closed_at           TIMESTAMPTZ,
    -- Au-delà de cet instant, plus aucune écriture n'est acceptée.
    grace_jusqua        TIMESTAMPTZ,
    -- Version juridique créée par CETTE séance, s'il y en a une : les PutFile
    -- suivants la mettent à jour au lieu d'en empiler une par enregistrement
    -- automatique. Une séance d'édition = une version.
    version_document_id UUID,
    last_put_at         TIMESTAMPTZ,
    put_count           INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_wopi_sessions_expiration CHECK (expires_at > created_at)
);

-- Recherche par jeton : le chemin chaud, appelé à chaque requête WOPI.
CREATE UNIQUE INDEX IF NOT EXISTS ux_dataroom_wopi_sessions_token
    ON dataroom_wopi_sessions (token_hash);

-- Purge des séances échues, et lecture des séances vivantes d'un document.
CREATE INDEX IF NOT EXISTS idx_dataroom_wopi_sessions_doc
    ON dataroom_wopi_sessions (workspace_id, document_id);
CREATE INDEX IF NOT EXISTS idx_dataroom_wopi_sessions_expiration
    ON dataroom_wopi_sessions (expires_at);

COMMENT ON TABLE dataroom_wopi_sessions IS
    'Séances d''édition bureautique (Collabora / WOPI). Porte le jeton d''accès '
    '(sous forme d''empreinte) et la trace de qui a édité quoi et quand.';

-- =====================================================================
-- TRACE D'ÉDITION MANUELLE SUR LE DOCUMENT
--
-- « Le document passe en modifié manuellement au premier PutFile. Cet état doit
-- être visible et persistant. » Il l'est ici, sur la ligne du document — pas
-- dans une session éphémère : la séance se purge, l'acte reste.
--
-- Cet état commande aussi l'avertissement de régénération : régénérer repart
-- des variables et efface les retouches. Sans cette date, l'interface ne peut
-- pas nommer ce qui sera perdu, et un avertissement générique se clique sans
-- se lire.
-- =====================================================================

ALTER TABLE dataroom_documents
    ADD COLUMN IF NOT EXISTS edite_manuellement_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS edite_par             UUID;

COMMENT ON COLUMN dataroom_documents.edite_manuellement_at IS
    'Date de la dernière édition manuelle (Collabora). NULL = document tel que '
    'généré. Alimente l''avertissement nominatif de « Regénérer ».';
