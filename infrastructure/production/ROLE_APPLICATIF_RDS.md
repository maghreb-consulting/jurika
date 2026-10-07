# Rôle applicatif `jurika_app` — procédure pour RDS (et toute base existante)

Lot L0 (sécurité serveur). Ce document décrit comment préparer une base PostgreSQL **avant le
premier démarrage** des services, quand `infrastructure/scripts/init-db.sh` ne peut pas
s'exécuter : Amazon RDS, sauvegarde restaurée, base déjà initialisée.

## Pourquoi deux rôles

| Rôle | Usage | Attributs |
|---|---|---|
| Propriétaire (`POSTGRES_USER`, ex. `jurika_user` ; sur RDS, l'utilisateur maître ou un rôle propriétaire dédié) | **Flyway uniquement** : crée les tables, les politiques RLS, les fonctions | propriétaire des objets |
| `jurika_app` | **Exécution des services** (datasource) | `LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION`, non propriétaire, droits DML seulement |

Un superutilisateur ou un rôle `BYPASSRLS` ignore la Row Level Security, et un propriétaire de
table l'ignore aussi sauf `FORCE ROW LEVEL SECURITY`. Le cloisonnement des cabinets (principe P9)
n'est effectif que si les services se connectent en `jurika_app`.

## Les migrations échouent si `jurika_app` est absent

Les migrations qui accordent les droits de `jurika_app` (auth V32, ticket V27, dataroom V33,
workflow V14) **s'arrêtent en erreur** si le rôle n'existe pas : elles ne passent pas en silence.
Elles ne créent jamais le rôle et ne contiennent aucun mot de passe. **Le rôle doit donc exister
avant le premier démarrage des services.**

## Procédure

1. Choisir un mot de passe fort pour `jurika_app`, distinct du mot de passe propriétaire, et le
   conserver dans le gestionnaire de secrets (variable `JURIKA_APP_PASSWORD`).
2. Depuis un poste qui atteint la base, avec `psql` 15 ou plus récent (le script utilise
   `\getenv`), connecté avec le **rôle propriétaire** :

   ```bash
   PGHOST=<hote> PGPORT=5432 PGUSER=<proprietaire> PGPASSWORD=<mot de passe proprietaire> \
   JURIKA_APP_PASSWORD=<mot de passe jurika_app> \
   infrastructure/scripts/rattrapage-role-applicatif.sh jurika_db jurika_billing
   ```

   Le script est idempotent : il crée le rôle s'il manque, impose ses attributs et son mot de
   passe, accorde les droits DML sur les tables et séquences existantes, et pose les droits par
   défaut pour les objets que le propriétaire créera ensuite.
3. Sur RDS, l'utilisateur maître n'est pas superutilisateur (`rds_superuser`) : la création du
   rôle et les `ALTER DEFAULT PRIVILEGES` du propriétaire fonctionnent. **Ne jamais** accorder
   `rds_superuser` ni `BYPASSRLS` à `jurika_app`.
4. Configurer les services : datasource en `jurika_app` / `JURIKA_APP_PASSWORD`, Flyway avec le
   rôle propriétaire (`spring.flyway.user` / `spring.flyway.password`).
5. Contrôler, toujours connecté avec le propriétaire :

   ```sql
   SELECT rolname, rolsuper, rolbypassrls, rolcanlogin FROM pg_roles WHERE rolname = 'jurika_app';
   -- attendu : jurika_app | f | f | t
   ```

   Vérifier chaque droit **séparément** : `has_table_privilege(role, table, 'SELECT,INSERT')`
   renvoie vrai dès qu'**un seul** des droits listés est détenu.

## Changer le mot de passe

Rejouer le script avec la nouvelle valeur de `JURIKA_APP_PASSWORD`, puis redémarrer les services.
