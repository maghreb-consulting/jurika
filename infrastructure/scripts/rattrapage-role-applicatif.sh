#!/bin/bash
# Rattrapage du role applicatif jurika_app sur une base EXISTANTE (lot L0).
#
# init-db.sh ne s'execute qu'a la creation d'un volume vide : une base deja
# initialisee (serveur de developpement, sauvegarde restauree, RDS) doit etre
# mise au meme etat par ce script. Il est IDEMPOTENT : on peut le rejouer.
#
#   - cree jurika_app s'il est absent ;
#   - impose ses attributs : LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB
#     NOCREATEROLE NOREPLICATION, et son mot de passe ;
#   - accorde, dans chaque base indiquee : CONNECT, USAGE sur le schema public,
#     SELECT/INSERT/UPDATE/DELETE sur toutes les tables, USAGE/SELECT sur
#     toutes les sequences, et les memes droits par defaut pour les objets que
#     le role courant (proprietaire, celui de Flyway) creera ensuite.
#
# Aucun droit DDL, TRUNCATE ni REFERENCES. Le script ne supprime rien.
#
# Usage (connexion avec le role PROPRIETAIRE des tables) :
#   PGHOST=... PGPORT=... PGUSER=<proprietaire> PGPASSWORD=... \
#   JURIKA_APP_PASSWORD=... ./rattrapage-role-applicatif.sh jurika_db [jurika_billing]
#
# Le mot de passe de jurika_app est lu par psql (\getenv) : il n'apparait ni
# dans la ligne de commande ni dans le texte SQL.
set -euo pipefail

: "${JURIKA_APP_PASSWORD:?JURIKA_APP_PASSWORD est obligatoire (mot de passe du role jurika_app)}"
export JURIKA_APP_PASSWORD

if [ "$#" -lt 1 ]; then
    echo "Usage : $0 <base> [<base> ...]" >&2
    exit 2
fi

premiere_base="$1"

# Role : cree s'il est absent, attributs et mot de passe imposes.
psql -v ON_ERROR_STOP=1 --no-psqlrc --dbname "$premiere_base" <<'EOSQL'
\getenv app_pwd JURIKA_APP_PASSWORD
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'jurika_app') THEN
        CREATE ROLE jurika_app;
    END IF;
END
$$;
ALTER ROLE jurika_app WITH LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION
    PASSWORD :'app_pwd';
EOSQL

# Droits, base par base.
for base in "$@"; do
    psql -v ON_ERROR_STOP=1 --no-psqlrc --dbname "$base" <<'EOSQL'
SELECT format('GRANT CONNECT ON DATABASE %I TO jurika_app', current_database()) \gexec
GRANT USAGE ON SCHEMA public TO jurika_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO jurika_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO jurika_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO jurika_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO jurika_app;
EOSQL
    echo "jurika_app : droits accordes sur la base ${base}"
done

echo "Rattrapage termine : jurika_app (NOSUPERUSER, NOBYPASSRLS) sur : $*"
