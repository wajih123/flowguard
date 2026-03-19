#!/usr/bin/env bash
# ============================================================
# FlowGuard — One-Time Server Init Script
# ============================================================
# Run this ONCE on a fresh Hetzner server.
# After this, deploys happen via: scripts/deploy.sh
#
# Usage:
#   curl -sL https://raw.githubusercontent.com/.../server-init.sh | bash
# OR copy to server, then:
#   bash scripts/server-init.sh
# ============================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { echo -e "${GREEN}[init]${NC} $*"; }
warn() { echo -e "${YELLOW}[init]${NC} $*"; }
fail() { echo -e "${RED}[init]${NC} FAILED: $*" >&2; exit 1; }

DOMAIN="157-180-43-233.sslip.io"
REPO_URL="${REPO_URL:-git@github.com:YOUR_ORG/flowguard.git}"
APP_DIR="/root/flowguard/backend"
EMAIL="${CERTBOT_EMAIL:-admin@flowguard.fr}"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log "FlowGuard Server Init — $(date)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ── 1. System updates ──────────────────────────────────────────────────────────
log "[1/7] Updating system packages..."
apt-get update -qq && apt-get upgrade -y -qq
apt-get install -y -qq curl git ufw fail2ban unattended-upgrades

# ── 2. Install Docker ─────────────────────────────────────────────────────────
log "[2/7] Installing Docker..."
if ! command -v docker &>/dev/null; then
    curl -fsSL https://get.docker.com | sh
    systemctl enable docker
    systemctl start docker
    log "Docker installed: $(docker --version)"
else
    log "Docker already installed: $(docker --version)"
fi

# ── 3. Firewall ──────────────────────────────────────────────────────────────
log "[3/7] Configuring UFW firewall..."
ufw allow 22/tcp    comment "SSH"
ufw allow 80/tcp    comment "HTTP (Certbot + redirect)"
ufw allow 443/tcp   comment "HTTPS"
ufw --force enable
log "UFW enabled: SSH(22), HTTP(80), HTTPS(443)"

# ── 4. Clone repo ─────────────────────────────────────────────────────────────
log "[4/7] Cloning repository..."
mkdir -p /root/flowguard
if [[ -d "$APP_DIR/.git" ]]; then
    warn "Repo already exists at $APP_DIR — skipping clone"
else
    git clone "$REPO_URL" /root/flowguard
fi
cd "$APP_DIR"

# ── 5. Create .env ────────────────────────────────────────────────────────────
log "[5/7] Creating .env file..."
if [[ -f ".env" ]]; then
    warn ".env already exists — skipping (delete it manually to recreate)"
else
    cat > .env <<'ENVEOF'
# ============================================================
# FlowGuard Production Environment Variables
# FILL IN ALL VALUES BEFORE STARTING
# ============================================================

# Database
DB_USER=flowguard
DB_NAME=flowguard
# REQUIRED — generate with: openssl rand -base64 32
DB_PASSWORD=CHANGE_ME

# Redis
# Optional — leave blank to disable auth
REDIS_PASSWORD=

# Security
# REQUIRED — generate with: openssl rand -base64 32
ENCRYPTION_KEY=CHANGE_ME

# JWT keys (defaults to PEM files bundled in jar)
# JWT_PRIVATE_KEY_LOCATION=privateKey.pem
# JWT_PUBLIC_KEY_LOCATION=publicKey.pem

# Email (SMTP)
SMTP_HOST=
SMTP_PORT=587
SMTP_USER=
SMTP_PASSWORD=

# Banking integrations
BRIDGE_CLIENT_ID=
BRIDGE_CLIENT_SECRET=
BRIDGE_REDIRECT_URL=https://157-180-43-233.sslip.io/banking/callback

# CORS
CORS_ORIGINS=https://157-180-43-233.sslip.io

# Swan
FLOWGUARD_SWAN_API_URL=https://api.swan.io
FLOWGUARD_SWAN_CLIENT_ID=
FLOWGUARD_SWAN_CLIENT_SECRET=
ENVEOF
    warn "⚠️  IMPORTANT: Edit /root/flowguard/backend/.env and fill in DB_PASSWORD and ENCRYPTION_KEY!"
    warn "   Run: nano /root/flowguard/backend/.env"
fi

# ── 6. Start infra only (postgres, redis) ─────────────────────────────────────
log "[6/7] Starting infrastructure services (postgres + redis)..."
# Only start database services first — nginx needs certs before it can start
docker compose up -d postgres redis
log "Waiting for postgres to be healthy..."
timeout 60 bash -c 'until docker exec flowguard-postgres pg_isready -U flowguard; do sleep 2; done'
log "Postgres is ready"

# ── 7. Get SSL certificate ────────────────────────────────────────────────────
log "[7/7] Obtaining Let's Encrypt SSL certificate..."

# Ensure webroot dir exists
mkdir -p /var/www/certbot

# Start temporary HTTP-only nginx for ACME challenge
# We need HTTP to get the cert, then restart with full HTTPS config
if docker ps --format "{{.Names}}" | grep -q "flowguard-nginx"; then
    warn "Nginx already running — skipping cert bootstrap"
else
    # Start nginx in HTTP-only mode to serve ACME challenge
    docker run -d --name certbot-nginx \
        -p 80:80 \
        -v /var/www/certbot:/var/www/certbot \
        nginx:alpine \
        sh -c "echo 'server{listen 80;location /.well-known/acme-challenge/{root /var/www/certbot;}}' > /etc/nginx/conf.d/default.conf && nginx -g 'daemon off;'"

    # Get certificate
    docker run --rm \
        -v /etc/letsencrypt:/etc/letsencrypt \
        -v /var/www/certbot:/var/www/certbot \
        certbot/certbot certonly \
            --webroot --webroot-path=/var/www/certbot \
            --email "$EMAIL" \
            --agree-tos \
            --no-eff-email \
            -d "$DOMAIN"

    docker rm -f certbot-nginx
    log "SSL certificate obtained for $DOMAIN"
fi

# ── Final: start all services ────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if grep -q "CHANGE_ME" .env 2>/dev/null; then
    warn "⚠️  .env still has CHANGE_ME values — update it before starting:"
    warn "   nano /root/flowguard/backend/.env"
    warn "   Then run: docker compose up -d"
else
    log "Starting all services..."
    docker compose up -d
    sleep 5
    docker compose ps
fi

echo ""
log "Server init complete!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Next steps:"
echo "  1. Add your deploy SSH public key to GitHub Actions secrets:"
echo "     SERVER_HOST = 157.180.43.233"
echo "     SERVER_SSH_KEY = (contents of your ~/.ssh/id_ed25519)"
echo ""
echo "  2. Push code to main — GitHub Actions will auto-deploy!"
echo ""
echo "  Site will be live at: https://$DOMAIN"
