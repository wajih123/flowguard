#!/usr/bin/env bash
# ============================================================
# FlowGuard — Production Deploy Script (Image-based)
# ============================================================
# Pulls pre-built images from Docker Hub (built by GitHub Actions CI)
# Usage:
#   bash infra/deploy.sh          # deploy all services
#   bash infra/deploy.sh backend  # deploy backend only
# ============================================================
set -euo pipefail

DEPLOY_DIR="/opt/flowguard"
COMPOSE="docker compose -f ${DEPLOY_DIR}/infra/docker-compose.prod.yml --env-file ${DEPLOY_DIR}/.env"
SERVICE="${1:-all}"
DOCKER_ORG="${DOCKER_ORG:-nevyo}"
DOCKER_TAG="${DOCKER_TAG:-latest}"

log()  { echo "[deploy] $*"; }
fail() { echo "[deploy] FAILED: $*" >&2; exit 1; }

cd "$DEPLOY_DIR"

# ── Preflight ────────────────────────────────────────────────────────────────
log "🔍 Running preflight checks..."
[[ -f .env ]] || fail ".env not found at ${DEPLOY_DIR}/.env"
[[ -f keys/privateKey.pem ]] || fail "JWT private key not found at ${DEPLOY_DIR}/keys/privateKey.pem"
[[ -f keys/publicKey.pem  ]] || fail "JWT public key not found at ${DEPLOY_DIR}/keys/publicKey.pem"

# ── Pull latest code ─────────────────────────────────────────────────────────
log "📥 Pulling latest code from main..."
git pull origin main

# ── Docker Hub authentication ────────────────────────────────────────────────
log "🐳 Authenticating with Docker registry..."
if [[ -n "${DOCKER_USERNAME:-}" ]] && [[ -n "${DOCKER_PASSWORD:-}" ]]; then
    echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
fi

# ── Pull latest images ───────────────────────────────────────────────────────
log "📦 Pulling latest Docker images..."
if [[ "$SERVICE" == "all" ]]; then
  docker pull ${DOCKER_ORG}/flowguard-backend:${DOCKER_TAG}
  docker pull ${DOCKER_ORG}/flowguard-web:${DOCKER_TAG}
  docker pull ${DOCKER_ORG}/flowguard-ml:${DOCKER_TAG}
  
  log "🔄 Starting all services (zero-downtime)..."
  $COMPOSE up -d --no-build backend
  $COMPOSE up -d --no-build ml-service
  $COMPOSE up -d --no-build web
  $COMPOSE up -d --no-build nginx certbot postgres redis
else
  docker pull ${DOCKER_ORG}/flowguard-${SERVICE}:${DOCKER_TAG}
  
  log "🔄 Restarting ${SERVICE} only (other services stay up)..."
  $COMPOSE up -d --no-build --force-recreate "$SERVICE"
fi

# ── Wait for backend health ──────────────────────────────────────────────────
log "⏳ Waiting for backend to be healthy..."
for i in $(seq 1 30); do
  if curl -sf http://localhost:8080/health > /dev/null 2>&1; then
    log "✅ Backend healthy after ${i}s"
    break
  fi
  if [[ $i -eq 30 ]]; then
    fail "Backend not healthy after 30 attempts — check: docker logs flowguard-backend --tail 50"
  fi
  sleep 3
done

# ── Final status ─────────────────────────────────────────────────────────────
log "📊 Deployment complete! Container status:"
docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}" | grep flowguard | grep -v exporter | grep -v promtail || true

# ── Docker Hub logout ────────────────────────────────────────────────────────
if [[ -n "${DOCKER_USERNAME:-}" ]]; then
  log "🧹 Logging out from Docker registry..."
  docker logout
fi

log "✨ All done!"
