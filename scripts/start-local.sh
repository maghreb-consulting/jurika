#!/usr/bin/env bash
# scripts/start-local.sh
# Sprint Beta (pricing-deploy) — TASK 8.
#
# Build + Up de la stack DEMO complete (10 services + infra) sur un
# SERVEUR LOCAL accessible via LAN. Attend que tous les healthchecks
# passent et affiche les URLs.
#
# Pre-requis : Docker Engine 24+ avec compose v2.20+.
# Usage :     ./scripts/start-local.sh           # build + up + wait
#             ./scripts/start-local.sh --no-build
#             ./scripts/start-local.sh --logs    # tail logs apres up
#             ./scripts/start-local.sh --reset   # down -v puis up

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

NO_BUILD=0
DO_LOGS=0
RESET=0
for arg in "$@"; do
  case "$arg" in
    --no-build) NO_BUILD=1 ;;
    --logs)     DO_LOGS=1 ;;
    --reset)    RESET=1 ;;
    *) echo "Option inconnue : $arg" >&2; exit 1 ;;
  esac
done

# ─── 1) Pre-flight ───────────────────────────────────────────────────
echo "🔍 Pre-flight checks"
command -v docker >/dev/null 2>&1 || { echo "❌ docker absent du PATH"; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "❌ docker compose v2 absent (besoin de v2.20+)"; exit 1; }

if [[ ! -f .env.local ]]; then
  echo "❌ .env.local introuvable."
  echo "   Copie .env.local.server.example en .env.local et personnalise."
  exit 1
fi

# Source pour recuperer JURIKA_LAN_HOST (utilisee en affichage final).
set -a; . ./.env.local; set +a
LAN_HOST="${JURIKA_LAN_HOST:-localhost}"

# Verifier que les cles JWT existent
if [[ ! -f infrastructure/secrets/jwt/jwt-public.pem ]]; then
  echo "⚠️  Cles JWT RS256 absentes — generation a la volee dans infrastructure/secrets/jwt/"
  mkdir -p infrastructure/secrets/jwt
  openssl genrsa -out infrastructure/secrets/jwt/jwt-private.pem 2048
  openssl pkcs8 -topk8 -inform PEM -in infrastructure/secrets/jwt/jwt-private.pem \
    -out infrastructure/secrets/jwt/jwt-private-pkcs8.pem -nocrypt
  openssl rsa -in infrastructure/secrets/jwt/jwt-private.pem -pubout \
    -out infrastructure/secrets/jwt/jwt-public.pem
  echo "  ✓ Paire RSA 2048 generee."
fi

COMPOSE_ARGS=(
  -f infrastructure/docker-compose.yml
  -f infrastructure/docker-compose.services.yml
  -f infrastructure/docker-compose.local.yml
  --env-file .env
  --env-file .env.local
  -p jurika-local
)

# ─── 2) Reset optionnel ─────────────────────────────────────────────
if [[ $RESET -eq 1 ]]; then
  echo "🗑️  --reset : down -v (volumes effaces, donnees perdues)"
  docker compose "${COMPOSE_ARGS[@]}" down -v --remove-orphans || true
fi

# ─── 3) Build (sauf --no-build) ─────────────────────────────────────
if [[ $NO_BUILD -eq 0 ]]; then
  echo "🔨 docker compose build (peut prendre 5-10 min la 1ere fois)"
  docker compose "${COMPOSE_ARGS[@]}" build --parallel
fi

# ─── 4) Up ──────────────────────────────────────────────────────────
echo "🚀 docker compose up -d"
docker compose "${COMPOSE_ARGS[@]}" up -d

# ─── 5) Attente healthchecks ────────────────────────────────────────
EXPECTED=(
  jurika-postgres jurika-redis jurika-rabbitmq jurika-mailhog
  jurika-discovery jurika-gateway jurika-auth jurika-ticket
  jurika-workflow jurika-dataroom jurika-ai jurika-supervision
  jurika-dashboard jurika-billing
  jurika-realtime jurika-ocr jurika-kie
  jurika-frontend
)
echo "⏳ Attente des healthchecks (timeout 5 min)"
DEADLINE=$(( $(date +%s) + 300 ))
while true; do
  HEALTHY=0
  TOTAL=0
  for c in "${EXPECTED[@]}"; do
    TOTAL=$((TOTAL + 1))
    state=$(docker inspect --format='{{.State.Health.Status}}' "$c" 2>/dev/null || echo "missing")
    if [[ "$state" == "healthy" ]]; then
      HEALTHY=$((HEALTHY + 1))
    fi
  done
  printf "\r   %d/%d services healthy   " "$HEALTHY" "$TOTAL"
  if [[ $HEALTHY -eq $TOTAL ]]; then
    echo
    break
  fi
  if [[ $(date +%s) -gt $DEADLINE ]]; then
    echo
    echo "❌ Timeout — voici l'etat actuel :"
    docker compose "${COMPOSE_ARGS[@]}" ps
    exit 2
  fi
  sleep 5
done

# ─── 6) Affichage URLs ──────────────────────────────────────────────
echo
echo "✅ Stack JURIKA UP sur LAN ${LAN_HOST}"
echo
echo "   App (frontend)      :  http://${LAN_HOST}/"
echo "   API Gateway         :  http://${LAN_HOST}:8080/actuator/health"
echo "   Realtime (Socket)   :  http://${LAN_HOST}:3000/health"
echo "   Discovery (Eureka)  :  http://${LAN_HOST}:8761"
echo "   OCR (docTR)         :  http://${LAN_HOST}:8089/health"
echo "   KIE (Donut)         :  http://${LAN_HOST}:8088/health"
echo "   MailHog UI          :  http://${LAN_HOST}:8025"
echo "   RabbitMQ Mgmt       :  http://${LAN_HOST}:15672  (user=${RABBITMQ_USER:-jurika})"
echo "   MinIO Console       :  http://${LAN_HOST}:9001"
echo
echo "   Comptes demo (apres seed) :"
echo "     Superviseur : superviseur@demo.jurika.ma / Demo@2026"
echo "     Employe     : employe1@demo.jurika.ma   / Demo@2026"
echo "     Client      : client@demo.jurika.ma     / Demo@2026"
echo "     Code workspace : JUR-DEMO2"
echo
echo "ℹ️  Pour seeder les donnees demo : node scripts/seed-demo.mjs"
echo "ℹ️  Pour le smoke test : ./scripts/smoke-test.sh"
echo

if [[ $DO_LOGS -eq 1 ]]; then
  docker compose "${COMPOSE_ARGS[@]}" logs -f --tail=50
fi
