#!/usr/bin/env bash
# ============================================================
# FlowGuard — Production Deploy Script
# ============================================================
# Zero-downtime: never calls `docker compose down`
# Usage:
#   bash infra/deploy.sh          # deploy all services
#   bash infra/deploy.sh backend  # deploy backend only
# ============================================================
set -euo pipefail

DEPLOY_DIR="/opt/flowguard"
COMPOSE="docker compose -f ${DEPLOY_DIR}/infra/docker-compose.prod.yml --env-file ${DEPLOY_DIR}/.env"
SERVICE="${1:-all}"

log()  { echo "[deploy] $*"; }
fail() { echo "[deploy] FAILED: $*" >&2; exit 1; }

cd "$DEPLOY_DIR"

# ── Preflight ────────────────────────────────────────────────────────────────
[[ -f .env ]] || fail ".env not found at ${DEPLOY_DIR}/.env"
[[ -f keys/privateKey.pem ]] || fail "JWT private key not found at ${DEPLOY_DIR}/keys/privateKey.pem"
[[ -f keys/publicKey.pem  ]] || fail "JWT public key not found at ${DEPLOY_DIR}/keys/publicKey.pem"

# ── Pull latest code ─────────────────────────────────────────────────────────
log "Pulling latest code..."
git pull origin main

# ── Build & Deploy ───────────────────────────────────────────────────────────
if [[ "$SERVICE" == "all" ]]; then
  log "Building all images..."
  $COMPOSE build --pull backend ml-service web

  log "Starting all services (zero-downtime rolling up)..."
  $COMPOSE up -d --no-deps backend
  $COMPOSE up -d --no-deps ml-service
  $COMPOSE up -d --no-deps web
  $COMPOSE up -d --no-deps nginx certbot postgres redis
else
  log "Building ${SERVICE}..."
  $COMPOSE build --pull "$SERVICE"
  log "Restarting ${SERVICE} only (other services stay up)..."
  $COMPOSE up -d --no-deps --force-recreate "$SERVICE"
fi

# ── Wait for backend health ──────────────────────────────────────────────────
log "Waiting for backend to be healthy..."
for i in $(seq 1 30); do
  if curl -sf http://localhost:8080/health > /dev/null 2>&1; then
    log "Backend healthy after ${i}s"
    break
  fi
  if [[ $i -eq 30 ]]; then
    fail "Backend not healthy after 30 attempts — check: docker logs flowguard-backend --tail 50"
  fi
  sleep 3
done

# ── Final status ─────────────────────────────────────────────────────────────
log "=== Deployment complete ==="
docker ps --format "table {{.Names}}\t{{.Status}}" | grep flowguard | grep -v exporter | grep -v promtail
