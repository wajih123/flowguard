#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# FlowGuard — ONE-TIME Server Setup Script
# Run once on a fresh Hetzner Ubuntu server:
#   bash <(curl -sL https://raw.githubusercontent.com/YOUR_ORG/flowguard/main/infra/setup-server.sh)
# Or: scp this file → ssh root@157.180.43.233 → bash setup-server.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

REPO_URL="${REPO_URL:?'Set REPO_URL before running: export REPO_URL=https://github.com/YOUR_USER/flowguard.git'}"
DEPLOY_DIR="/opt/flowguard"
SERVER_IP="157.180.43.233"

echo "════════════════════════════════════════════════════"
echo " FlowGuard Server Setup  —  $(date)"
echo "════════════════════════════════════════════════════"

# ── 0. System update ──────────────────────────────────────────────────────────
apt-get update -qq && apt-get upgrade -y -qq

# ── 1. Install Docker ─────────────────────────────────────────────────────────
if ! command -v docker &>/dev/null; then
  echo "[1/7] Installing Docker..."
  apt-get install -y -qq ca-certificates curl gnupg
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
    gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
    https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -qq
  apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-compose-plugin
  systemctl enable --now docker
else
  echo "[1/7] Docker already installed: $(docker --version)"
fi

# ── 2. Install useful tools ───────────────────────────────────────────────────
echo "[2/7] Installing tools..."
apt-get install -y -qq git curl wget jq unzip ufw

# ── 3. Firewall setup ─────────────────────────────────────────────────────────
echo "[3/7] Configuring firewall..."
ufw --force reset
ufw default deny incoming
ufw default allow outgoing
ufw allow ssh
ufw allow http
ufw allow https
ufw --force enable

# ── 4. Clone repository ───────────────────────────────────────────────────────
echo "[4/7] Cloning repository..."
if [ -d "$DEPLOY_DIR" ]; then
  echo "    → $DEPLOY_DIR exists, pulling latest..."
  cd "$DEPLOY_DIR" && git pull origin main
else
  git clone "$REPO_URL" "$DEPLOY_DIR"
fi
cd "$DEPLOY_DIR"

# ── 5. Create .env.prod if missing ────────────────────────────────────────────
echo "[5/7] Setting up environment..."
if [ ! -f "$DEPLOY_DIR/infra/.env.prod" ]; then
  cp "$DEPLOY_DIR/infra/.env.prod.example" "$DEPLOY_DIR/infra/.env.prod"
  echo ""
  echo "  ⚠️  IMPORTANT: Edit $DEPLOY_DIR/infra/.env.prod and fill in:"
  echo "       - POSTGRES_PASSWORD (use a strong password)"
  echo "       - REDIS_PASSWORD    (use a strong password)"
  echo "       - BRIDGE_CLIENT_ID / BRIDGE_CLIENT_SECRET"
  echo "       - CORS_ORIGINS, BRIDGE_REDIRECT_URL"
  echo ""
  echo "  Then re-run: cd $DEPLOY_DIR && bash infra/deploy.sh"
  exit 0
fi

# ── 6. Build & start services ─────────────────────────────────────────────────
echo "[6/7] Building Docker images (this takes ~5 minutes)..."
cd "$DEPLOY_DIR"
docker compose -f infra/docker-compose.prod.yml \
  --env-file infra/.env.prod \
  build --no-cache --parallel

echo "[6/7] Starting services..."
docker compose -f infra/docker-compose.prod.yml \
  --env-file infra/.env.prod \
  up -d

# ── 7. Verify ─────────────────────────────────────────────────────────────────
echo "[7/7] Waiting for services to be healthy (~60s)..."
sleep 60
docker compose -f infra/docker-compose.prod.yml \
  --env-file infra/.env.prod \
  ps

echo ""
echo "════════════════════════════════════════════════════"
echo " Setup complete! FlowGuard is running at:"
echo "   http://$SERVER_IP"
echo ""
echo " Next steps to enable HTTPS:"
echo "   1. Point flowguard.fr DNS A-record → $SERVER_IP"
echo "   2. Run: bash $DEPLOY_DIR/infra/enable-https.sh flowguard.fr admin@flowguard.fr"
echo "════════════════════════════════════════════════════"
