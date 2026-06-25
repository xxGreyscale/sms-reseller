#!/usr/bin/env bash
# scripts/start.sh — One-command full-stack dev boot for sms-reseller
#
# Sequence:
#   1. Fail fast if .env is missing
#   2. Source .env into the current shell
#   3. Bring up Docker Compose infra (postgres, redis, rabbitmq)
#   4. Poll healthchecks until all three are healthy (no sleep-based wait)
#   5. Launch 8 Spring Boot services in background (separate bootRun per service)
#   6. Launch admin-web (Next.js dev server) in background
#   7. Print summary with ports
#
# Port map (also documented in .env.example):
#   identity   8081   catalog  8082   wallet  8083   payment  8084
#   contact    8085   messaging 8086  notification 8087   admin  8088
#   admin-web  3000
#
# Usage:
#   ./scripts/start.sh
#
# Logs:  .logs/<service>.log
# PIDs:  .logs/pids
# Stop:  ./scripts/stop.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# ---------------------------------------------------------------------------
# 1. .env check
# ---------------------------------------------------------------------------
if [[ ! -f .env ]]; then
  echo "ERROR: .env not found. Copy the example and fill in your values:"
  echo "  cp .env.example .env"
  exit 1
fi

# ---------------------------------------------------------------------------
# 2. Source .env
# ---------------------------------------------------------------------------
set -a
# shellcheck disable=SC1091
source .env
set +a

# ---------------------------------------------------------------------------
# 3. Docker Compose up
# ---------------------------------------------------------------------------
echo ">> Starting infrastructure (postgres, redis, rabbitmq)..."
docker compose up -d

# ---------------------------------------------------------------------------
# 4. Wait for all services to be healthy using compose healthchecks (no sleep)
# ---------------------------------------------------------------------------
echo ">> Waiting for infrastructure to become healthy..."
INFRA_SERVICES="postgres redis rabbitmq"
TIMEOUT=120
ELAPSED=0
INTERVAL=3

while true; do
  ALL_HEALTHY=true
  for svc in $INFRA_SERVICES; do
    # docker compose ps --format json | filter is cumbersome; use --filter instead
    STATUS=$(docker compose ps --format "{{.Service}} {{.Health}}" 2>/dev/null | awk -v s="$svc" '$1==s {print $2}')
    if [[ "$STATUS" != "healthy" ]]; then
      ALL_HEALTHY=false
      break
    fi
  done

  if $ALL_HEALTHY; then
    echo ">> Infrastructure healthy."
    break
  fi

  ELAPSED=$((ELAPSED + INTERVAL))
  if [[ $ELAPSED -ge $TIMEOUT ]]; then
    echo "ERROR: Infrastructure did not become healthy within ${TIMEOUT}s."
    docker compose ps
    exit 1
  fi

  # Poll again (no raw sleep in the happy path — only during the wait loop)
  sleep $INTERVAL
done

# ---------------------------------------------------------------------------
# 5. Launch 8 Spring Boot services
# ---------------------------------------------------------------------------
mkdir -p .logs
: > .logs/pids  # truncate/create PID file

declare -A SVC_PORTS
SVC_PORTS[identity]=8081
SVC_PORTS[catalog]=8082
SVC_PORTS[wallet]=8083
SVC_PORTS[payment]=8084
SVC_PORTS[contact]=8085
SVC_PORTS[messaging]=8086
SVC_PORTS[notification]=8087
SVC_PORTS[admin]=8088

echo ">> Launching Spring Boot services..."

for SVC in identity catalog wallet payment contact messaging notification admin; do
  PORT="${SVC_PORTS[$SVC]}"
  DB_URL="jdbc:postgresql://localhost:5432/${SVC}"
  LOG=".logs/${SVC}.log"

  echo "   -> ${SVC}-service  port=${PORT}  db=${DB_URL}  log=${LOG}"

  SERVER_PORT="$PORT" \
  SPRING_DATASOURCE_URL="$DB_URL" \
  ./gradlew ":services:${SVC}-service:bootRun" \
    --args='--spring.profiles.active=dev' \
    > "$LOG" 2>&1 &

  echo $! >> .logs/pids
done

# ---------------------------------------------------------------------------
# 6. Launch admin-web (Next.js)
# ---------------------------------------------------------------------------
ADMINWEB_LOG=".logs/admin-web.log"
echo "   -> admin-web        port=3000  log=${ADMINWEB_LOG}"

(cd apps/admin-web && npm run dev > "../../${ADMINWEB_LOG}" 2>&1) &
echo $! >> .logs/pids

# ---------------------------------------------------------------------------
# 7. Summary
# ---------------------------------------------------------------------------
echo ""
echo ">> Stack is starting. Services will be ready in ~30-60s."
echo ""
echo "   Service        Port   Log"
echo "   identity       8081   .logs/identity.log"
echo "   catalog        8082   .logs/catalog.log"
echo "   wallet         8083   .logs/wallet.log"
echo "   payment        8084   .logs/payment.log"
echo "   contact        8085   .logs/contact.log"
echo "   messaging      8086   .logs/messaging.log"
echo "   notification   8087   .logs/notification.log"
echo "   admin          8088   .logs/admin.log"
echo "   admin-web      3000   .logs/admin-web.log"
echo ""
echo "   Infra: postgres:5432  redis:6379  rabbitmq:5672 (mgmt: 15672)"
echo ""
echo "   Health smoke-check (run after ~60s):"
echo "     curl -s localhost:8081/actuator/health"
echo "     curl -s localhost:8084/actuator/health"
echo ""
echo "   Stop everything: ./scripts/stop.sh"
echo ""
echo "   PIDs: .logs/pids"
