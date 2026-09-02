-- =====================================================================
-- BUG 7 (2026-06-08) — Dissociation identifiant de connexion vs email de
-- contact.
--
-- Avant : `users.email` = a la fois identifiant d'authentification ET email
-- de notification. Probleme : si le user change d'email personnel ou si on
-- veut un identifiant stable et professionnel @jurika.ma, on ne peut pas le
-- faire sans casser le login.
--
-- Apres :
--   - `users.login_email`   = identifiant de connexion stable (format
--                              prenom.nom@jurika.ma genere, UNIQUE par
--                              workspace). C'est ce qui est compare au
--                              champ "email" du formulaire de login.
--   - `users.contact_email` = email personnel ou pro de l'utilisateur, sert
--                              UNIQUEMENT a recevoir les notifications
--                              (welcome, invite, reset password, nouvelle
--                              IP, ...). Modifiable plus tard sans casser
--                              le login.
--   - `users.email`         = colonne historique conservee pour ne casser
--                              aucune lecture legacy (audit/log/SQL ad-hoc).
--                              Backfillee = login_email (compat exacte avec
--                              les anciens comptes ou login_email = email
--                              actuel).
--
-- Strategie de backfill RETROCOMPATIBLE :
--   - login_email   := email (verbatim) pour tous les comptes existants
--   - contact_email := email (verbatim) pour tous les comptes existants
--
-- Consequence : aucun compte ne perd son login. Les NOUVEAUX comptes (signup
-- + invite-employee + invite-client) generent un login_email
-- `prenom.nom@jurika.ma` distinct du contact_email (= email perso fourni
-- par l'inviteur).
--
-- Feature flag : `jurika.auth.login-email-enabled` (defaut TRUE). Quand
-- desactive, LoginUseCase ignore login_email et utilise uniquement
-- `users.email` -> bascule rollback instantanee sans migration inverse.
-- =====================================================================

ALTER TABLE users ADD COLUMN login_email   VARCHAR(150);
ALTER TABLE users ADD COLUMN contact_email VARCHAR(150);

-- Backfill : copie 1:1 de l'email actuel.
UPDATE users SET login_email = email, contact_email = email
    WHERE login_email IS NULL OR contact_email IS NULL;

ALTER TABLE users ALTER COLUMN login_email   SET NOT NULL;
ALTER TABLE users ALTER COLUMN contact_email SET NOT NULL;

-- Unicite par workspace (un cabinet ne peut pas avoir deux Karim Benali avec
-- le meme login_email -- le generateur LoginEmailGenerator ajoute un suffixe
-- pour resoudre la collision). NB : deux workspaces differents PEUVENT avoir
-- chacun un karim.benali@jurika.ma, le workspace_code resoud l'ambiguite a
-- la connexion.
ALTER TABLE users ADD CONSTRAINT uq_users_workspace_login_email
    UNIQUE (workspace_id, login_email);

CREATE INDEX idx_users_login_email ON users (login_email);
CREATE INDEX idx_users_contact_email ON users (contact_email);
