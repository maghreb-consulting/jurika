#!/bin/bash
# Sauvegarde controlee avant recreation (procedure /lot-complet, etape 7.4 ; CLAUDE.md).
# Usage : scripts/sauvegarde-lot.sh <dossier de sauvegarde>
# Les consommateurs (services applicatifs) doivent etre arretes avant l'appel : la base et le
# volume MinIO ne bougent plus pendant la copie. Aucun secret affiche.
#
# Controles (un seul en echec => code 1, "CONTROLE SAUVEGARDE : ECHEC") :
#  - pg_dump -Fc de toutes les bases non modeles : taille non nulle, pg_restore --list code 0 ;
#  - pg_dumpall --globals-only : au moins 2 CREATE ROLE ;
#  - volume MinIO : copie fichier par fichier, relue (sauvegarde-volume-par-exec.py) ;
#  - lot L6 (E2) : DOCUMENTS reels de MinIO (hors fichiers internes .minio.sys) rapproches des
#    documents en base, cles lues a l'instant de la sauvegarde (controle-sauvegarde-minio.py).
set -u
D="$1"
RACINE="$(cd "$(dirname "$0")/.." && pwd)"
PGU=$(grep -hE '^POSTGRES_USER=' "$RACINE/.env" "$RACINE/.env.local" 2>/dev/null | tail -1 | cut -d= -f2)
PGU="${PGU:-jurika_user}"
umask 077
mkdir -p "$D" && chmod 700 "$D"
ok=1
cp "$RACINE/.env.local" "$D/env.local" && cmp -s "$RACINE/.env.local" "$D/env.local" || { echo "ECHEC copie env"; ok=0; }
bases=$(docker exec jurika-postgres psql -U "$PGU" -d postgres -Atc "select datname from pg_database where not datistemplate order by 1")
for b in $bases; do
  docker exec jurika-postgres pg_dump -U "$PGU" -Fc "$b" > "$D/$b.dump" || { echo "ECHEC pg_dump $b"; ok=0; continue; }
  taille=$(stat -c %s "$D/$b.dump")
  docker exec -i jurika-postgres pg_restore --list < "$D/$b.dump" > "$D/$b.list"
  rc=$?
  n=$(grep -vc '^;' "$D/$b.list")
  echo "$b : $taille o, $n entrees, pg_restore --list code $rc"
  [ "$taille" -gt 0 ] && [ "$rc" -eq 0 ] || ok=0
done
docker exec jurika-postgres pg_dumpall -U "$PGU" --globals-only > "$D/globals.sql" || ok=0
roles=$(grep -c '^CREATE ROLE' "$D/globals.sql")
echo "globals : $(stat -c %s "$D/globals.sql") o, $roles CREATE ROLE"
[ "$roles" -ge 2 ] || ok=0
# Cles des documents en base, lues avant la copie du volume (services arretes).
python3 -c "import importlib.util as u; s=u.spec_from_file_location('c','$RACINE/scripts/controle-sauvegarde-minio.py'); m=u.module_from_spec(s); s.loader.exec_module(m); print(m.CLES_EN_BASE)" \
  | docker exec -i jurika-postgres psql -U "$PGU" -d jurika_db -At > "$D/cles_documents_en_base.txt" || ok=0
python3 "$RACINE/scripts/sauvegarde-volume-par-exec.py" jurika-minio /data "$D/minio_data.tar.gz" || ok=0
python3 "$RACINE/scripts/controle-sauvegarde-minio.py" "$D/minio_data.tar.gz" "$D/cles_documents_en_base.txt" || ok=0
chmod 600 "$D"/*
if [ $ok -eq 1 ]; then echo "CONTROLE SAUVEGARDE : OK"; else echo "CONTROLE SAUVEGARDE : ECHEC"; exit 1; fi
