-- =====================================================================
-- JURIKA V29 — Verrous d'édition WOPI
-- Lot 4 (2026-09-07) — deux employés ne s'écrasent plus l'un l'autre
--
-- LE PROBLÈME QU'ELLE RÉSOUT
-- Au lot 3, rien n'empêchait deux employés d'ouvrir le même acte. Chacun
-- éditait sa copie chargée en mémoire ; le dernier à enregistrer écrasait le
-- travail du premier. Aucune erreur, aucun message : la version juridique
-- finale ne contenait qu'une des deux séries de corrections, et personne ne
-- pouvait savoir laquelle avait disparu. Sur un acte juridique, une perte
-- d'édition silencieuse est inacceptable.
--
-- POURQUOI `document_id` EST LA CLÉ PRIMAIRE
-- L'invariant à tenir est « au plus un verrou vivant par document ». Le poser
-- comme clé primaire le fait tenir par la BASE, pas par une vérification
-- applicative qu'une exécution concurrente pourrait contourner entre le SELECT
-- et l'INSERT. Deux poses simultanées : l'une passe, l'autre reçoit une
-- violation de contrainte, et c'est le comportement voulu.
--
-- POURQUOI UNE EXPIRATION
-- Un navigateur qui plante ne relâche pas son verrou. Sans expiration, l'acte
-- resterait bloqué jusqu'à une intervention manuelle. Le protocole WOPI prévoit
-- exactement cela : l'éditeur rafraîchit son verrou périodiquement, et un
-- verrou non rafraîchi devient reprenable. La reprise est journalisée — elle
-- signifie qu'une séance a été interrompue, et peut-être du travail avec.
-- =====================================================================

CREATE TABLE IF NOT EXISTS dataroom_wopi_verrous (
    -- Un document, au plus un verrou. L'invariant est tenu par la base.
    document_id         UUID PRIMARY KEY,
    workspace_id        UUID         NOT NULL,
    -- Identifiant de verrou choisi par l'éditeur, rendu tel quel dans l'en-tête
    -- `X-WOPI-Lock`. Le protocole autorise jusqu'à 1024 caractères.
    lock_id             VARCHAR(1024) NOT NULL,
    -- La séance qui détient le verrou : sert à savoir QUI prévenir et à
    -- relâcher le verrou quand la séance se ferme.
    session_id          UUID         NOT NULL,
    user_id             UUID         NOT NULL,
    -- Nom affiché à l'employé qui arrive en second. « En cours d'édition »
    -- sans dire par qui n'aide personne à décrocher son téléphone.
    user_display_name   VARCHAR(200),
    acquis_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expire_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_wopi_verrous_expiration CHECK (expire_at > acquis_at)
);

-- Purge et reprise des verrous échus.
CREATE INDEX IF NOT EXISTS idx_dataroom_wopi_verrous_expiration
    ON dataroom_wopi_verrous (expire_at);

-- « Quel acte cet employé tient-il ouvert ? » — relâchement à la fermeture.
CREATE INDEX IF NOT EXISTS idx_dataroom_wopi_verrous_session
    ON dataroom_wopi_verrous (session_id);

COMMENT ON TABLE dataroom_wopi_verrous IS
    'Verrous d''édition bureautique (WOPI Lock). Un document = au plus un '
    'verrou vivant. Empêche deux employés de s''écraser mutuellement sur un '
    'acte juridique.';

COMMENT ON COLUMN dataroom_wopi_verrous.expire_at IS
    'Au-delà, le verrou est reprenable : un navigateur qui plante ne relâche '
    'rien, et un acte bloqué indéfiniment serait pire que le risque couvert.';
