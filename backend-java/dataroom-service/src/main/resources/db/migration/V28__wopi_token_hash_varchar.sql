-- =====================================================================
-- JURIKA V28 — `token_hash` en VARCHAR plutôt qu'en CHAR
-- Lot 3 (2026-09-07)
--
-- V27 déclarait `token_hash CHAR(64)`. Hibernate mappe un `String` sur
-- `varchar` : au démarrage, la validation de schéma refusait la table —
--
--   Schema-validation: wrong column type encountered in column [token_hash]
--   in table [dataroom_wopi_sessions]; found [bpchar (Types#CHAR)],
--   but expecting [varchar(64) (Types#VARCHAR)]
--
-- — et le service redémarrait en boucle (8 redémarrages avant diagnostic).
--
-- V27 N'EST PAS ÉDITÉE. Elle est déjà appliquée, et ce projet tourne avec
-- `validate-on-migrate: false` : réécrire une migration jouée serait
-- SILENCIEUSEMENT ignoré sur les bases existantes, et l'écart persisterait
-- sans que rien ne le signale. La correction passe donc par une migration
-- nouvelle, idempotente, qui converge aussi bien sur une base existante que
-- sur une installation neuve.
--
-- Au passage, `CHAR` était de toute façon le mauvais choix : il complète à
-- droite par des espaces, ce qui ferait échouer une comparaison d'empreinte
-- au moindre écart de longueur.
-- =====================================================================

ALTER TABLE dataroom_wopi_sessions
    ALTER COLUMN token_hash TYPE VARCHAR(64);
