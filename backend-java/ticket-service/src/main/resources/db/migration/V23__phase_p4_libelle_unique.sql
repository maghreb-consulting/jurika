-- =====================================================================
-- JURIKA V23 — Un seul libellé pour la phase P4 (lot 2, 2026-09-07)
--
-- DÉFAUT CORRIGÉ
-- Le référentiel portait DEUX libellés sous le même code de phase :
--   P4 = « P4 Capital »       (étape 16)
--   P4 = « P4 Enregistrement » (étapes 17 et 18)
-- L'API regroupe les démarches par CODE de phase et retient le libellé de la
-- première ligne rencontrée : les étapes 17 et 18 s'affichaient donc sous
-- « P4 Capital », et « P4 Enregistrement » n'apparaissait nulle part. Un
-- employé cherchant l'enregistrement du bail ne trouvait pas la phase qui le
-- porte.
--
-- CE QUE DIT LE GUIDE (GUIDE_CREATION_ENTREPRISE_MAROC_SARL_v2.xlsx,
-- onglet « 1. Parcours création ») : une seule bannière de phase couvre les
-- étapes 16 à 18 — « PHASE 4 — CAPITAL ET ENREGISTREMENT ». Le dédoublement
-- venait de la dérivation, pas du guide. On revient donc au guide : un code,
-- un libellé, qui nomme les deux volets.
--
-- Les autres phases sont laissées telles quelles : leur libellé est déjà
-- unique par code (vérifié avant écriture de cette migration).
-- =====================================================================

UPDATE demarches_referentiel
   SET phase_libelle = 'P4 Capital et enregistrement'
 WHERE workflow_type = 'CREATION'
   AND phase_code = 'P4';

-- Pas de garde-fou en index : « un seul libellé par code » n'est pas une
-- contrainte d'unicité de lignes (plusieurs démarches partagent légitimement le
-- même couple code+libellé). L'invariant est vérifié par un test
-- (DemarchesReferentielIT) plutôt que par une contrainte qui échouerait à
-- l'installation.
