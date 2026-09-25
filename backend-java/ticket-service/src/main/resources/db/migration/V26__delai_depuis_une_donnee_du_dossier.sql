-- =====================================================================
-- JURIKA V26 — Un délai peut partir d'une DONNÉE du dossier, pas seulement
--              du cochage d'une autre ligne
--
-- CE QUI EXISTAIT
-- `delai_reference_ordre` : l'échéance se calcule depuis la date de cochage
-- d'une AUTRE ligne du parcours (« dans les 30 jours de l'acte » → la signature
-- des statuts, ligne 13). Huit délais sont mécanisés ainsi.
--
-- CE QUI MANQUAIT, ET POURQUOI ÇA IMPORTE
-- Deux obligations légales ne partent d'aucun geste du cabinet :
--
--   ligne 23 — inscription à la taxe professionnelle
--              « Dans les 30 jours du début d'activité »
--   ligne 32 — affiliation à la CNSS
--              « Dans les 30 jours du début d'activité ou de la première embauche »
--
-- Le début d'activité n'est pas une étape que l'on coche : c'est une DATE que le
-- client déclare. Le lot 1 a donc refusé — à raison — de fabriquer une échéance,
-- et ces deux délais sont restés AVEUGLES : l'interface affichait le texte du
-- délai en disant qu'elle ne savait pas le calculer.
--
-- Le lot B a ouvert le champ `$DATE_DEBUT_ACTIVITE` — la vingtième saisie
-- héritée, trouvée en lisant la fiche de renseignements, qui refusait de sortir
-- sur « Date de début d'activité envisagée : . ». La donnée existe désormais, et
-- ces deux délais cessent d'être aveugles.
--
-- LA RÈGLE NE CHANGE PAS : AUCUNE DATE FABRIQUÉE
-- Tant que la date n'est pas saisie, aucune échéance n'est calculée et aucune
-- alerte n'est levée — exactement comme une ligne dont l'étape de référence
-- n'est pas encore cochée. Un délai dont on ignore le départ ne se devine pas.
--
-- Les règles acquises au lot 1 valent telles quelles : mois calendaires, fuseau
-- figé à Africa/Casablanca, et le point de départ le PLUS PRÉCOCE quand le texte
-- est ambigu. La ligne 32 dit « ou » : le délai court du premier des deux
-- événements, et le début d'activité précède la première embauche dans le cas
-- général.
--
-- IDEMPOTENTE. Elle s'applique aussi bien à une base déjà migrée par V24 qu'à
-- une base neuve où V24 vient de poser `delai_valeur`/`delai_unite` sans la
-- colonne ci-dessous — celle-ci n'existait pas quand V24 a été écrite.
-- =====================================================================

ALTER TABLE demarches_referentiel
    ADD COLUMN IF NOT EXISTS delai_reference_donnee VARCHAR(60);

COMMENT ON COLUMN demarches_referentiel.delai_reference_donnee IS
    'Nom de la variable du dossier dont la valeur sert de point de départ au délai '
    '(ex. DATE_DEBUT_ACTIVITE). Exclusive de delai_reference_ordre : un délai a UN '
    'point de départ. NULL quand le délai part du cochage d''une autre ligne, ou '
    'quand il n''est pas mécanisable du tout.';

-- Un délai a UN point de départ. Deux le rendraient ambigu, et l'ambiguïté se
-- résoudrait silencieusement dans l'ordre où le code teste les colonnes.
ALTER TABLE demarches_referentiel
    DROP CONSTRAINT IF EXISTS ck_demarches_un_seul_point_de_depart;
ALTER TABLE demarches_referentiel
    ADD CONSTRAINT ck_demarches_un_seul_point_de_depart
    CHECK (delai_reference_ordre IS NULL OR delai_reference_donnee IS NULL);

-- chk_delai_complet (creee par V20) exigeait delai_reference_ordre des qu'un delai etait pose
-- (valeur + unite + reference_ordre : tout ou rien). Un delai ancre sur une DONNEE n'a pas de
-- reference_ordre — l'UPDATE ci-dessous pose (30,'JOURS') sans ordre et violerait cette contrainte.
-- On la remplace pour n'exiger que le couple valeur/unite ; le point de depart (ordre OU donnee)
-- est facultatif et gouverne par ck_demarches_un_seul_point_de_depart ci-dessus. Un delai sans
-- point de depart reste AVEUGLE (aucune echeance calculee), conformement a « aucune date fabriquee ».
ALTER TABLE demarches_referentiel
    DROP CONSTRAINT IF EXISTS chk_delai_complet;
ALTER TABLE demarches_referentiel
    ADD CONSTRAINT chk_delai_complet CHECK (
        (delai_valeur IS NULL AND delai_unite IS NULL)
     OR (delai_valeur IS NOT NULL AND delai_unite IS NOT NULL));

-- ---------------------------------------------------------------------
-- Les deux lignes concernées.
--
-- Le filtre porte sur `formalite_code`, et non sur l'ordre seul : un numéro de
-- ligne est un rang dans un classeur, il bougera à la prochaine livraison du
-- parcours. Le code de formalité, lui, désigne la démarche.
-- ---------------------------------------------------------------------
UPDATE demarches_referentiel
   SET delai_valeur = 30,
       delai_unite = 'JOURS',
       delai_reference_ordre = NULL,
       delai_reference_donnee = 'DATE_DEBUT_ACTIVITE'
 WHERE workflow_type = 'CREATION'
   AND actif
   AND formalite_volet = 'DEPOT'
   AND formalite_code IN ('TAXE_PROFESSIONNELLE', 'AFFILIATION_CNSS');

-- ---------------------------------------------------------------------
-- Garde-fou. Deux lignes, pas une, pas trois.
--
-- Si le parcours change et que l'une de ces deux formalités disparaît ou se
-- dédouble, la migration DOIT s'arrêter : un délai légal câblé sur la mauvaise
-- ligne alerterait sur la mauvaise démarche, et personne ne le verrait.
-- ---------------------------------------------------------------------
DO $$
DECLARE n INT;
BEGIN
    SELECT count(*) INTO n
      FROM demarches_referentiel
     WHERE workflow_type = 'CREATION' AND actif
       AND delai_reference_donnee = 'DATE_DEBUT_ACTIVITE';
    IF n <> 2 THEN
        RAISE EXCEPTION 'V26 : % ligne(s) rattachee(s) a DATE_DEBUT_ACTIVITE, 2 attendues '
                        '(taxe professionnelle et CNSS, volet DEPOT)', n;
    END IF;
END $$;

-- Aucune ligne ne doit porter les deux points de départ à la fois. La contrainte
-- ci-dessus le garantit pour l'avenir ; on vérifie ici l'existant.
DO $$
DECLARE n INT;
BEGIN
    SELECT count(*) INTO n
      FROM demarches_referentiel
     WHERE delai_reference_ordre IS NOT NULL AND delai_reference_donnee IS NOT NULL;
    IF n <> 0 THEN
        RAISE EXCEPTION 'V26 : % ligne(s) avec deux points de depart', n;
    END IF;
END $$;
