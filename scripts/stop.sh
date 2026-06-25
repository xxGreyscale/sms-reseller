#!/usr/bin/env bash
# scripts/stop.sh — Tear down the full local dev stack
#
# 1. Kill all background PIDs recorded by start.sh (.logs/pids)
# 2. Run docker compose down to stop and remove infra containers
#
# Usage:
#   ./scripts/stop.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# ---------------------------------------------------------------------------
# 1. Kill Spring Boot / Next.js processes
# ---------------------------------------------------------------------------
PIDS_FILE=".logs/pids"

if [[ -f "$PIDS_FILE" ]]; then
  echo ">> Stopping application processes..."
  while IFS= read -r pid; do
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      echo "   Killing PID $pid"
      kill "$pid" 2>/dev/null || true
    fi
  done < "$PIDS_FILE"
  rm -f "$PIDS_FILE"
  echo ">> Application processes stopped."
else
  echo ">> No PID file found at $PIDS_FILE — skipping process teardown."
fi

# ---------------------------------------------------------------------------
# 2. Docker Compose down
# ---------------------------------------------------------------------------
echo ">> Stopping infrastructure..."
docker compose down
echo ">> Infrastructure stopped."
echo ""
echo "   To also remove volumes (wipe all data): docker compose down -v"
