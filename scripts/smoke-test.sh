#!/usr/bin/env bash
# scripts/smoke-test.sh — wrapper bash
# Sprint Beta (pricing-deploy) — TASK 9.

set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec node "$PROJECT_ROOT/scripts/smoke-test.mjs" "$@"
