#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# FlowGuard — Deploy Script
# Usage (local machine → server):
#   bash infra/deploy.sh [user@server]
#
# Usage (run directly on server):
#   cd /opt/flowguard && bash infra/deploy.sh
#
# GitHub Actions calls this via SSH (see .github/workflows/ci-cd.yml)
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

DEPLOY_DIR="/opt/flowguard"
COMPOSE="docker compose -f ${DEPLOY_DIR}/infra/docker-compose.prod.yml --env-file ${DEPLOY_DIR}/infra/.env.prod"

# ─── If a remote target is given, SSH into it and re-run this script ──────────
if [ $# -ge 1 ]; then
  TARGET="$1"
  echo "→ Deploying to $TARGET"
  ssh -o StrictHostKeyChecking=no "$TARGET" "bash -s" <<'REMOTE'
    set -euo pipefail
    cd /opt/flowguard
    git fetch --all
    git reset --hard origin/main
    bash infra/deploy.sh
REMOTE
  echo "✓ Remote deploy complete"
  exit 0
fi

# ─── Local deploy (runs on the server itself) ─────────────────────────────────
echo "════════════════════════════════════════════════════"
echo " FlowGuard Deploy  —  $(date)"
echo " Commit: $(git -C $DEPLOY_DIR rev-parse --short HEAD 2>/dev/null || echo 'unknown')"
echo "════════════════════════════════════════════════════"

cd "$DEPLOY_DIR"

# Pull latest code
echo "[1/4] Pulling code..."
git fetch --all
git reset --hard origin/main

# Build images (sequential to avoid OOM on 4GB server)
echo "[2/4] Building backend image..."
$COMPOSE build backend

echo "[2/4] Building ML service image..."
$COMPOSE build ml-service

echo "[2/4] Building web image..."
$COMPOSE build web

# Rolling restart (zero-downtime: start new, stop old)
echo "[3/4] Restarting services..."
$COMPOSE up -d --remove-orphans

# Remove old dangling images to save disk space
docker image prune -f

# Health check
echo "[4/4] Health check..."
sleep 30

BACKEND_STATUS=$(docker inspect --format='{{.State.Health.Status}}' flowguard-backend 2>/dev/null || echo "unknown")
ML_STATUS=$(docker inspect --format='{{.State.Health.Status}}' flowguard-ml 2>/dev/null || echo "unknown")
NGINX_STATUS=$(docker inspect --format='{{.State.Status}}' flowguard-nginx 2>/dev/null || echo "unknown")

echo "  backend  → $BACKEND_STATUS"
echo "  ml       → $ML_STATUS"
echo "  nginx    → $NGINX_STATUS"

if [ "$BACKEND_STATUS" = "healthy" ] || [ "$BACKEND_STATUS" = "starting" ]; then
  echo "════════════════════════════════════════════════════"
  echo " ✓ Deploy successful!"
  echo "════════════════════════════════════════════════════"
else
  echo "════════════════════════════════════════════════════"
  echo " ⚠️  Backend not yet healthy (may still be starting)"
  echo "    Check logs: docker logs flowguard-backend --tail 50"
  echo "════════════════════════════════════════════════════"
fi
