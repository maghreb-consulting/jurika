#!/bin/bash
# JURIKA — rollback-staging.sh (Sprint 14 ter F5).
#
# Appele par le workflow .github/workflows/deploy-staging.yml job "rollback-on-failure"
# sur echec du deploy ou des smoke tests. Le script doit etre present sur la VM staging
# dans /opt/jurika/rollback-staging.sh (scp via le workflow ou setup initial manuel).
#
# Strategie : pour chaque service, recupere le tag de l'avant-derniere image (sha-XXXXXXX)
# disponible localement, remplace le tag :staging du compose par celui-ci, redemarre.
#
# Pre-requis :
#   - docker compose v2
#   - /opt/jurika/docker-compose.staging.yml present
#   - .env present avec les secrets
#
# Logs : /var/log/jurika/rollback.log
# Usage manuel : ssh deploy@staging.jurika.ma "cd /opt/jurika && bash rollback-staging.sh [service]"

set -euo pipefail

LOG_FILE=${LOG_FILE:-/var/log/jurika/rollback.log}
mkdir -p "$(dirname "$LOG_FILE")"
exec > >(tee -a "$LOG_FILE") 2>&1

COMPOSE_FILE=${COMPOSE_FILE:-docker-compose.staging.yml}
REGISTRY_PREFIX=${REGISTRY_PREFIX:-ghcr.io/ou55am1}
SERVICES=(discovery auth ticket workflow dataroom dashboard supervision ai gateway frontend)

echo "════ JURIKA rollback-staging.sh $(date -Iseconds) ════"

if [ ! -f "$COMPOSE_FILE" ]; then
  echo "ERREUR : $COMPOSE_FILE introuvable"
  exit 2
fi

ROLLBACK_TAG=""

for svc in "${SERVICES[@]}"; do
  image_repo="${REGISTRY_PREFIX}/jurika-${svc}"
  prev=$(docker images --format '{{.Repository}}:{{.Tag}}' "$image_repo" 2>/dev/null \
         | grep -E "^${image_repo}:sha-[a-f0-9]{7}$" \
         | head -n 2 | tail -n 1 \
         | awk -F: '{print $NF}')
  if [ -z "$prev" ]; then
    echo "[WARN] $svc : aucune image sha-* previous trouvee, skip"
    continue
  fi
  echo "[INFO] $svc : rollback vers tag $prev"
  if [ -z "$ROLLBACK_TAG" ]; then
    ROLLBACK_TAG="$prev"
  fi
  # Re-tag :staging vers la version previous (atomique)
  docker tag "${image_repo}:${prev}" "${image_repo}:staging"
done

if [ -z "$ROLLBACK_TAG" ]; then
  echo "ERREUR : aucun tag de rollback identifie"
  exit 3
fi

echo "[INFO] Redemarrage compose avec images re-taguees :staging"
docker compose -f "$COMPOSE_FILE" up -d --force-recreate

echo "[INFO] Wait 30s pour healthchecks…"
sleep 30
docker compose -f "$COMPOSE_FILE" ps

echo "════ rollback complete (tag base : $ROLLBACK_TAG) $(date -Iseconds) ════"
