-- =====================================================================
-- JURIKA V23.1 — chk_delai_complet doit permettre un delai ancre sur une DONNEE
--
-- V20 a cree demarches_referentiel avec chk_delai_complet exigeant le triple
-- (delai_valeur, delai_unite, delai_reference_ordre) tout ou rien. Or deux delais
-- legaux partent d'une DONNEE du dossier ($DATE_DEBUT_ACTIVITE), pas du cochage
-- d'une autre ligne :
--   ligne 23 — inscription a la taxe professionnelle
--   ligne 32 — affiliation a la CNSS
-- V24 les pose en (30, 'JOURS', NULL) et V26 les rattache a la donnee via la
-- colonne delai_reference_donnee. Cette combinaison (valeur + unite SANS
-- reference_ordre) VIOLE chk_delai_complet telle que V20 l'a ecrite : l'INSERT de
-- V24 echoue sur une base VIERGE. La base historique du portable le masquait (la
-- contrainte y preexistait dans un etat relache, jamais rejouee sur cette donnee).
--
-- POURQUOI ICI, et pas dans V20 ni V24
--   - V20 est APPLIQUEE : intouchable (validate-on-migrate: false rendrait toute
--     edition MUETTE).
--   - V24 est un fichier GENERE (derive-referentiel-demarches.mjs) : on n'y ajoute
--     pas de DDL a la main.
-- On relache donc la contrainte dans une migration dediee, intercalee entre V23 et
-- V24, sans toucher ni l'une ni l'autre. out-of-order: true (cf. application.yml)
-- garantit qu'elle s'applique aussi aux bases deja migrees au-dela de V24.
--
-- CE QUE LA CONTRAINTE GARANTIT DESORMAIS
-- On n'exige plus que le couple valeur/unite (les deux ensemble ou aucun). Le
-- point de depart (ordre OU donnee) devient facultatif et son unicite est gouvernee
-- par ck_demarches_un_seul_point_de_depart (posee par V26). Un delai chiffre sans
-- point de depart reste AVEUGLE : aucune echeance n'est calculee, conformement a
-- la regle « aucune date fabriquee ».
-- =====================================================================

ALTER TABLE demarches_referentiel
    DROP CONSTRAINT IF EXISTS chk_delai_complet;
ALTER TABLE demarches_referentiel
    ADD CONSTRAINT chk_delai_complet CHECK (
        (delai_valeur IS NULL AND delai_unite IS NULL)
     OR (delai_valeur IS NOT NULL AND delai_unite IS NOT NULL));
