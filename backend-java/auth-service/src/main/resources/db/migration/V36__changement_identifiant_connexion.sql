-- =====================================================================
-- JURIKA V36 (auth-service) -- Lot L5, etape E10 : gestion de son compte
--
-- CDC 3.6, RG-CPT-02 : le changement de l'adresse electronique ou de
-- l'identifiant de connexion exige une confirmation par lien ; tant que le
-- lien n'est pas confirme, l'ancienne valeur reste active.
--
-- Les jetons de confirmation reutilisent email_verification_tokens (V4) :
-- empreinte SHA-256 seule (jamais le jeton en clair), usage unique
-- (used_at, consommation atomique), expiration (expires_at), RLS par
-- workspace deja en place (politique email_verif_isolation, droits de
-- jurika_app accordes en V32). La colonne email porte la NOUVELLE valeur
-- demandee.
--
-- V4 autorise deja le motif EMAIL_CHANGE (adresse electronique). On ajoute
-- LOGIN_CHANGE (identifiant de connexion).
-- =====================================================================

ALTER TABLE email_verification_tokens
    DROP CONSTRAINT IF EXISTS email_verification_tokens_purpose_check;

ALTER TABLE email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_purpose_check
    CHECK (purpose IN ('EMAIL_VERIFICATION', 'EMAIL_CHANGE', 'LOGIN_CHANGE'));
