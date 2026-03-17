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

# Generate JWT signing keys (RSA 2048) on first deploy — never stored in git
KEYS_DIR="$DEPLOY_DIR/keys"
mkdir -p "$KEYS_DIR"
if [ ! -f "$KEYS_DIR/privateKey.pem" ]; then
  echo "[1/4] Generating JWT RSA-2048 key pair..."
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
    -out "$KEYS_DIR/privateKey.pem" 2>/dev/null
  openssl rsa -in "$KEYS_DIR/privateKey.pem" -pubout \
    -out "$KEYS_DIR/publicKey.pem" 2>/dev/null
  chmod 600 "$KEYS_DIR/privateKey.pem"
  echo "  JWT keys created at $KEYS_DIR"
else
  echo "[1/4] JWT keys already present — skipping generation"
fi

# Build images (sequential to avoid OOM on 4GB server)
# --pull : force pull of latest base image (avoids stale python:3.xx cache)
# All images must build successfully before ANY container is restarted.
# This prevents partial deploys that leave the app broken.

echo "[2/4] Building backend image..."
$COMPOSE build --pull backend || { echo "✗ Backend build failed — aborting, running containers untouched"; exit 1; }

echo "[2/4] Building ML service image..."
$COMPOSE build --pull ml-service || { echo "✗ ML service build failed — aborting, running containers untouched"; exit 1; }

echo "[2/4] Building web image..."
$COMPOSE build --pull web || { echo "✗ Web build failed — aborting, running containers untouched"; exit 1; }

# All builds succeeded → safe to restart
# Use `compose down` to properly stop all containers (including those with
# restart:unless-stopped like certbot), which avoids the race condition where
# Docker restarts a container between our `docker rm -f` and `compose up`.
echo "[3/4] Stopping all containers..."
$COMPOSE down --remove-orphans 2>/dev/null || true

# Force-remove any containers left over from a previous project name
# (happens when the compose `name:` was changed between deploys — the old
# containers were tracked under a different project and are invisible to
# `compose down`, but their container_name still blocks a fresh `compose up`).
for c in flowguard-db flowguard-redis flowguard-backend flowguard-ml \
          flowguard-web flowguard-nginx flowguard-certbot flowguard-autoheal; do
  docker rm -f "$c" 2>/dev/null || true
done

echo "[3/4] Starting services..."
$COMPOSE up -d

# Remove dangling images ONLY after successful restart (never prune before)
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
