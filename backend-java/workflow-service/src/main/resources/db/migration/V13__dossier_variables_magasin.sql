-- =====================================================================
-- JURIKA V13 — Le magasin de variables du dossier (lot C, 2026-09-11)
--
-- DÉCISION 2 DE LA DIRECTION : « Une variable se saisit une seule fois. La
-- dénomination est saisie une fois ; aucun document ne la redemande. Chaque
-- valeur occupe sa variable, disponible dès qu'un document en a besoin. »
--
-- ---------------------------------------------------------------------
-- CE QU'IL REMPLACE, ET POURQUOI CE N'EST PAS UN MÉCANISME DE PLUS
--
-- Jusqu'ici une valeur appartenait à L'ÉTAPE qui l'avait saisie :
-- `workflow_progress.data` est un JSONB dont la clé de premier niveau est
-- `step1`…`step9`. Rien dans le système ne connaissait « une variable ». La
-- charge utile des documents était reconstruite à chaque génération, dans le
-- NAVIGATEUR, par un chemin `stepData.stepN.<forme>.<clé>` recalculé à la
-- lecture.
--
-- Ce que ce chemin a coûté, constaté au lot C :
--   * dix champs de l'étape 9 (`$LIEU_SIGNATURE` — 21 gabarits sur 23 —,
--     `$NOMBRE_ORIGINAUX`, l'exercice social, le commissaire aux comptes)
--     étaient saisis, persistés, et n'atteignaient AUCUN document ;
--   * les signataires désignés à l'étape 5 étaient remplacés en silence par
--     les gérants, dans le bloc de signature des statuts ;
--   * `$VILLE` et `$SIEGE_VILLE` portaient la même valeur alors que les
--     imprimés DGI en attendent deux différentes.
--
-- Aucun de ces trois défauts ne produit de blanc : ils produisent une valeur
-- fausse et plausible, que rien ne signale.
--
-- CE MAGASIN EST DONC LA SOURCE, PAS UN CACHE. `workflow_progress.data`
-- conserve l'état des formulaires (ce que l'écran réaffiche) ; les VARIABLES
-- DE DOCUMENT vivent ici et nulle part ailleurs, et la génération ne lit
-- qu'ici. Il n'y a pas deux endroits où la même valeur peut vivre : il y a un
-- état d'écran et un magasin de variables, et un seul des deux est lu pour
-- produire un acte.
--
-- ---------------------------------------------------------------------
-- POURQUOI LA CLÉ EST LE TICKET, ALORS QUE LA DIRECTION DIT « LE DOSSIER »
--
-- Au parcours de CRÉATION, `entreprise_dossiers` n'existe pas encore pendant
-- la saisie : le dossier est créé À LA FINALISATION du workflow
-- (`WorkflowFinalizationService`). Clé-er le magasin sur le dossier rendrait
-- impossible d'y écrire quoi que ce soit avant la dernière étape — c'est-à-dire
-- pendant tout le temps où l'employé saisit.
--
-- Le ticket est donc la clé, et `dossier_id` est renseigné dès qu'il est connu
-- (à la finalisation pour une création, immédiatement pour toute opération sur
-- une société déjà au dossier). Le ticket d'une création EST le dossier en
-- train de se faire ; la colonne `dossier_id` porte le lien dès qu'il existe,
-- et c'est par elle qu'une opération ultérieure retrouvera les valeurs.
--
-- ---------------------------------------------------------------------
-- CE QUE LA BASE GARANTIT, PLUTÔT QUE LE CODE
--
--   1. UNE VARIABLE, UNE VALEUR — deux index uniques partiels, l'un pour les
--      variables simples, l'autre pour les occurrences de boucle. Deux lignes
--      pour `$DENOMINATION` sur le même ticket sont IMPOSSIBLES : c'est la
--      décision 2, tenue par la base et non par une convention d'appel.
--      (Un index unique ordinaire ne suffirait pas : en PostgreSQL deux NULL
--      sont distincts, et `boucle IS NULL` autoriserait les doublons.)
--   2. UNE OCCURRENCE DE BOUCLE PORTE SON RANG — `boucle` et `rang` sont
--      renseignés ensemble ou pas du tout.
--   3. LA PROVENANCE EST OBLIGATOIRE — `origine` dit d'où vient la valeur, et
--      une valeur SAISIE porte toujours son auteur. Une valeur corrigée doit
--      pouvoir l'être sans qu'on se demande d'où venait la précédente.
-- =====================================================================

CREATE TABLE dossier_variables (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id    UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,

    -- Le ticket porte le magasin pendant toute la saisie (cf. en-tête).
    ticket_id       UUID         NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    -- Renseigné dès que le dossier existe. NULL pendant une création en cours.
    dossier_id      UUID         REFERENCES entreprise_dossiers(id) ON DELETE CASCADE,

    -- Le nom de la variable SANS le `$` : « DENOMINATION », « SIEGE_VILLE ».
    variable        VARCHAR(80)  NOT NULL,
    -- Nom de la boucle (« ASSOCIES », « GERANTS », « SIGNATAIRES »…) ou NULL.
    boucle          VARCHAR(60),
    -- Rang 0-based de l'occurrence dans sa boucle, ou NULL hors boucle.
    rang            SMALLINT,

    valeur          TEXT,

    -- SAISIE  : un humain l'a tapée.
    -- BASE    : la plateforme la détenait déjà (dossier, ticket, société).
    -- DERIVEE : calculée depuis d'autres variables — jamais demandée.
    origine         VARCHAR(10)  NOT NULL
                    CHECK (origine IN ('SAISIE','BASE','DERIVEE')),

    -- Qui, quand, et à quelle occasion (« etape-5 », « CONTRAT_BAIL »…).
    saisie_par_id   UUID         REFERENCES users(id),
    saisie_le       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    occasion        VARCHAR(60),

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- Une occurrence de boucle porte son rang ; une variable simple n'en a pas.
    CONSTRAINT ck_dossier_variables_boucle_rang
        CHECK ((boucle IS NULL AND rang IS NULL) OR (boucle IS NOT NULL AND rang IS NOT NULL)),
    -- Une valeur saisie par un humain nomme cet humain.
    CONSTRAINT ck_dossier_variables_saisie_auteur
        CHECK (origine <> 'SAISIE' OR saisie_par_id IS NOT NULL)
);

-- UNE VARIABLE, UNE VALEUR — variables simples.
CREATE UNIQUE INDEX ux_dossier_variables_simple
    ON dossier_variables (workspace_id, ticket_id, variable)
    WHERE boucle IS NULL;

-- UNE VARIABLE, UNE VALEUR — occurrences de boucle.
CREATE UNIQUE INDEX ux_dossier_variables_boucle
    ON dossier_variables (workspace_id, ticket_id, boucle, rang, variable)
    WHERE boucle IS NOT NULL;

-- Lecture du magasin au montage d'une étape et à la génération.
CREATE INDEX idx_dossier_variables_ticket
    ON dossier_variables (workspace_id, ticket_id);

-- Lecture par dossier : une opération ultérieure retrouve ce qui a été saisi.
CREATE INDEX idx_dossier_variables_dossier
    ON dossier_variables (workspace_id, dossier_id)
    WHERE dossier_id IS NOT NULL;

ALTER TABLE dossier_variables ENABLE ROW LEVEL SECURITY;

CREATE POLICY dossier_variables_isolation ON dossier_variables FOR ALL
    USING (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid)
    WITH CHECK (workspace_id = NULLIF(current_setting('app.current_workspace_id', TRUE), '')::uuid);

CREATE TRIGGER trg_dossier_variables_updated_at
    BEFORE UPDATE ON dossier_variables
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

COMMENT ON TABLE dossier_variables IS
    'Magasin de variables du dossier (lot C). Source UNIQUE des variables de '
    'document : la generation ne lit que cette table. workflow_progress.data '
    'conserve l''etat des formulaires, jamais les variables des actes.';

COMMENT ON COLUMN dossier_variables.origine IS
    'SAISIE (un humain l''a tapee, saisie_par_id obligatoire) | BASE (la '
    'plateforme la detenait deja) | DERIVEE (calculee, jamais demandee).';

COMMENT ON COLUMN dossier_variables.dossier_id IS
    'NULL tant que le dossier n''existe pas — au parcours de CREATION il est '
    'cree a la finalisation. Renseigne des qu''il est connu.';
