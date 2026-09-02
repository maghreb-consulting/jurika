#!/usr/bin/env bash
# JURIKA — Telecharge l'agent OpenTelemetry Java auto-instrumentation (Sprint 2 / TASK 3).
# A executer UNE FOIS par poste de dev. Le jar (~25MB) est gitignore.

set -euo pipefail

OTEL_VERSION="${OTEL_VERSION:-2.10.0}"
TARGET_DIR="$(cd "$(dirname "$0")" && pwd)"
TARGET_FILE="$TARGET_DIR/opentelemetry-javaagent.jar"

if [ -f "$TARGET_FILE" ]; then
  echo "OK — agent deja present : $TARGET_FILE"
  echo "    Pour forcer le re-telechargement : rm \"$TARGET_FILE\" && $0"
  exit 0
fi

URL="https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_VERSION}/opentelemetry-javaagent.jar"

echo "→ Telechargement OpenTelemetry Java agent v${OTEL_VERSION}..."
echo "  URL : $URL"

if command -v curl >/dev/null 2>&1; then
  curl -fsSL -o "$TARGET_FILE" "$URL"
elif command -v wget >/dev/null 2>&1; then
  wget -q -O "$TARGET_FILE" "$URL"
else
  echo "ERREUR : ni curl ni wget disponible" >&2
  exit 1
fi

echo "OK — agent telecharge : $TARGET_FILE"
echo "Taille : $(du -h "$TARGET_FILE" | cut -f1)"
echo ""
echo "Prochaine etape : monter ce jar dans chaque service Java via le Dockerfile :"
echo "  COPY opentelemetry-javaagent.jar /app/otel.jar"
echo "  ENV JAVA_TOOL_OPTIONS=\"-javaagent:/app/otel.jar\""
