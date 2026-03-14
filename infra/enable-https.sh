#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# FlowGuard — Enable HTTPS with Let's Encrypt
# Run AFTER DNS is pointing to the server
#   bash infra/enable-https.sh flowguard.fr admin@flowguard.fr
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

DOMAIN="${1:-flowguard.fr}"
EMAIL="${2:-admin@flowguard.fr}"
DEPLOY_DIR="/opt/flowguard"
COMPOSE="docker compose -f ${DEPLOY_DIR}/infra/docker-compose.prod.yml --env-file ${DEPLOY_DIR}/infra/.env.prod"

echo "Requesting SSL certificate for $DOMAIN (email: $EMAIL)..."

# Issue certificate via certbot standalone (nginx must be up for HTTP challenge)
$COMPOSE run --rm certbot certonly \
  --webroot \
  --webroot-path /var/www/certbot \
  --email "$EMAIL" \
  --agree-tos \
  --no-eff-email \
  -d "$DOMAIN" \
  -d "www.$DOMAIN"

echo "✓ Certificate issued. Switching to HTTPS nginx config..."

# Swap nginx config
cp "$DEPLOY_DIR/infra/nginx-https.conf" "$DEPLOY_DIR/infra/nginx.conf"

# Reload nginx
$COMPOSE exec nginx nginx -s reload

echo "════════════════════════════════════════════════════"
echo " ✓ HTTPS enabled for https://$DOMAIN"
echo " Auto-renewal: certbot container renews every 12h"
echo "════════════════════════════════════════════════════"

# Update CORS origins and Bridge redirect URL in .env.prod
sed -i "s|CORS_ORIGINS=.*|CORS_ORIGINS=https://$DOMAIN,https://www.$DOMAIN|" "$DEPLOY_DIR/infra/.env.prod"
sed -i "s|BRIDGE_REDIRECT_URL=.*|BRIDGE_REDIRECT_URL=https://$DOMAIN/banking/callback|" "$DEPLOY_DIR/infra/.env.prod"

# Restart backend with updated env
$COMPOSE up -d backend
echo "✓ Backend restarted with updated CORS and Bridge redirect URL"
