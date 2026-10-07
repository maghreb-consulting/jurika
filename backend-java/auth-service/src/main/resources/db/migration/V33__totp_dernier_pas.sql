-- Lot L0 (securite serveur), etape E10d -- anti-rejeu TOTP (RFC 6238, section 5.2).
--
-- Dernier pas de temps TOTP accepte pour l'utilisateur (temps Unix / 30 s).
-- Un code n'est accepte que si son pas est STRICTEMENT superieur : verification
-- et enregistrement se font en une seule requete atomique
--   UPDATE users SET totp_dernier_pas = :pas
--   WHERE id = :id AND (totp_dernier_pas IS NULL OR totp_dernier_pas < :pas)
-- et le code n'est accepte que si une ligne est modifiee. Un code rejoue, ou
-- presente deux fois en meme temps, n'est donc accepte qu'une seule fois.
-- NULL : aucun code TOTP encore accepte.

ALTER TABLE users ADD COLUMN IF NOT EXISTS totp_dernier_pas BIGINT;
