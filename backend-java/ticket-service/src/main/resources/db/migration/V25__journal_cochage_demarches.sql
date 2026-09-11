-- =====================================================================
-- JURIKA V25 — Le cochage d'une demarche devient un JOURNAL
--
-- CE QUI EXISTAIT, ET POURQUOI CA NE SUFFIT PAS
-- `ticket_demarches` porte UN etat, UNE date (`coche_at`), UN acteur. Chaque
-- geste ECRASE le precedent : `DemarcheUseCases.decocher` repositionne l'etat a
-- A_FAIRE avec `coche_at = NULL`, et la date du cochage disparait. Sans trace.
--
-- Sur des demarches administratives reelles, « quand avons-nous depose ? » se
-- pose des mois plus tard, et la reponse ne doit pas dependre de ce qu'on a fait
-- de la case depuis. Une demarche decochee puis recochee doit garder les TROIS
-- evenements.
--
-- Ce n'est donc pas une case a decocher : c'est un journal. `ticket_demarches`
-- garde l'etat COURANT — c'est ce que l'ecran affiche, et le lire ne doit pas
-- couter un repli d'historique — et cette table garde ce qui s'est passe.
--
-- L'ANNULATION EXIGE UN MOTIF. Decision du cabinet, portee par une contrainte
-- plutot que par du code : « annulation sans motif » ne doit pas pouvoir
-- s'ecrire, meme par une migration ou un script.
-- =====================================================================

CREATE TABLE ticket_demarche_evenements (
    id                 UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id       UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    ticket_demarche_id UUID        NOT NULL REFERENCES ticket_demarches(id) ON DELETE CASCADE,
    -- COCHAGE        : la demarche est accomplie.
    -- ANNULATION     : un cochage est annule. Motif obligatoire.
    -- HORS_PERIMETRE : la demarche conditionnelle est ecartee. Motif obligatoire.
    -- REPRISE        : une demarche ecartee revient au perimetre.
    type               VARCHAR(20) NOT NULL
                       CHECK (type IN ('COCHAGE','ANNULATION','HORS_PERIMETRE','REPRISE')),
    motif              TEXT,
    acteur_id          UUID        REFERENCES users(id) ON DELETE SET NULL,
    survenu_le         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Nombre de justificatifs attaches AU MOMENT du geste. Les documents, eux,
    -- vivent dans ticket_demarche_justificatifs ; ce compteur dit ce que
    -- l'evenement portait, meme si une piece est retiree ensuite.
    justificatifs      SMALLINT    NOT NULL DEFAULT 0,

    -- Annuler un cochage ou ecarter une demarche sans dire pourquoi n'a aucune
    -- valeur probante. La contrainte le rend impossible, pas seulement improbable.
    CONSTRAINT chk_evenement_motif CHECK (
        type NOT IN ('ANNULATION','HORS_PERIMETRE')
     OR (motif IS NOT NULL AND length(btrim(motif)) > 0))
);

-- L'index porte le tri : un journal se lit dans l'ordre, et toujours pour une
-- demarche donnee.
CREATE INDEX idx_tde_demarche ON ticket_demarche_evenements (ticket_demarche_id, survenu_le);
CREATE INDEX idx_tde_workspace ON ticket_demarche_evenements (workspace_id, survenu_le DESC);

ALTER TABLE ticket_demarche_evenements ENABLE ROW LEVEL SECURITY;
CREATE POLICY tde_isolation ON ticket_demarche_evenements FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

-- ---------------------------------------------------------------------
-- Reprise de l'existant
--
-- Les cochages deja poses n'ont qu'une date et un acteur : on en fait un
-- evenement, pas deux. Ce qui a ete decoche AVANT cette migration est perdu —
-- c'est irrattrapable, l'information n'a jamais ete ecrite — et on ne fabrique
-- pas d'evenement pour le masquer.
-- ---------------------------------------------------------------------
INSERT INTO ticket_demarche_evenements
       (workspace_id, ticket_demarche_id, type, motif, acteur_id, survenu_le, justificatifs)
SELECT td.workspace_id, td.id,
       CASE td.etat WHEN 'COCHEE' THEN 'COCHAGE' ELSE 'HORS_PERIMETRE' END,
       -- Le CHECK exige un motif pour HORS_PERIMETRE. Les lignes historiques en
       -- ont un (contrainte V21) ; le COALESCE couvre le cas theorique d'une
       -- ligne posee hors application.
       CASE td.etat WHEN 'COCHEE' THEN td.motif
            ELSE COALESCE(NULLIF(btrim(td.motif), ''),
                          'Motif non consigne avant la mise en place du journal (V25).') END,
       td.acteur_id,
       COALESCE(td.coche_at, td.created_at),
       (SELECT count(*) FROM ticket_demarche_justificatifs j WHERE j.ticket_demarche_id = td.id)
  FROM ticket_demarches td
 WHERE td.etat <> 'A_FAIRE';

COMMENT ON TABLE ticket_demarche_evenements IS
    'Journal des gestes poses sur une demarche. ticket_demarches porte l''etat COURANT ; '
    'cette table porte ce qui s''est passe. Un cochage annule conserve SES DEUX horodatages.';
