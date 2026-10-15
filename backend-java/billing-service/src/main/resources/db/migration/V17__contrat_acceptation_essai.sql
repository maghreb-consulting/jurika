-- =====================================================================
-- JURIKA V17 (billing-service) -- Lot L7 (E13) : mention de l'essai gratuit
-- dans l'exemplaire du contrat d'abonnement (decision du 2026-10-14).
--
-- A l'acceptation du contrat, billing lit chez auth la version et l'instant
-- d'acceptation des conditions de l'essai du cabinet (piece D, RG-CTR-10) et
-- les FIGE dans la preuve : la mention "Conditions d'utilisation de l'essai
-- gratuit (version ...) acceptees le ..." est inseree dans le texte accepte
-- (donc hachee) ; l'exemplaire, regenere depuis la preuve, la porte a
-- l'identique et verifie qu'elle concorde avec ces colonnes.
--
-- Colonnes nullables : NULL = le cabinet n'avait pas eu d'essai a
-- l'acceptation (reponse explicite d'auth), et toutes les acceptations
-- anterieures a cette migration (aucune mention dans leur texte).
--
-- Inalterabilite : ADD COLUMN sans valeur par defaut ne reecrit aucune ligne
-- et ne declenche pas trg_contrat_acceptations_inalterable (BEFORE UPDATE OR
-- DELETE, par ligne) ; le declencheur couvre les nouvelles colonnes comme les
-- autres. Droits de jurika_app inchanges (SELECT, INSERT sur la table, V13).
-- =====================================================================

ALTER TABLE contrat_acceptations
    ADD COLUMN IF NOT EXISTS essai_conditions_version     VARCHAR(60),
    ADD COLUMN IF NOT EXISTS essai_conditions_acceptees_le TIMESTAMPTZ;

ALTER TABLE contrat_acceptations DROP CONSTRAINT IF EXISTS contrat_acceptations_essai_complet;
ALTER TABLE contrat_acceptations ADD CONSTRAINT contrat_acceptations_essai_complet
    CHECK ((essai_conditions_version IS NULL) = (essai_conditions_acceptees_le IS NULL));

COMMENT ON COLUMN contrat_acceptations.essai_conditions_version IS
    'L7 E13 : version des conditions de l essai gratuit acceptees par le cabinet (auth essai_conditions_acceptations), figee a l acceptation ; NULL sans essai.';
COMMENT ON COLUMN contrat_acceptations.essai_conditions_acceptees_le IS
    'L7 E13 : instant d acceptation des conditions de l essai, fige a l acceptation du contrat ; NULL sans essai.';
