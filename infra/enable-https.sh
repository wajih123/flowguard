#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# FlowGuard — Enable HTTPS with Let's Encrypt
# Run AFTER DNS is pointing to the server
#   bash infra/enable-https.sh flowguard.fr admin@flowguard.fr
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

DOMAIN="${1:-157-180-43-233.sslip.io}"
EMAIL="${2:-admin@flowguard.fr}"
DEPLOY_DIR="/opt/flowguard"
COMPOSE="docker compose -f ${DEPLOY_DIR}/infra/docker-compose.prod.yml --env-file ${DEPLOY_DIR}/infra/.env.prod"

echo "Requesting SSL certificate for $DOMAIN (email: $EMAIL)..."

# Issue certificate via certbot webroot (nginx must already be running on port 80)
# sslip.io hostnames have no www. subdomain — only request the bare domain
if [[ "$DOMAIN" == *".sslip.io" ]]; then
  CERTBOT_DOMAINS="-d $DOMAIN"
else
  CERTBOT_DOMAINS="-d $DOMAIN -d www.$DOMAIN"
fi

$COMPOSE run --rm certbot certonly \
  --webroot \
  --webroot-path /var/www/certbot \
  --email "$EMAIL" \
  --agree-tos \
  --no-eff-email \
  $CERTBOT_DOMAINS

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
# For sslip.io domains there is no www. subdomain
if [[ "$DOMAIN" == *".sslip.io" ]]; then
  sed -i "s|CORS_ORIGINS=.*|CORS_ORIGINS=https://$DOMAIN|" "$DEPLOY_DIR/infra/.env.prod"
else
  sed -i "s|CORS_ORIGINS=.*|CORS_ORIGINS=https://$DOMAIN,https://www.$DOMAIN|" "$DEPLOY_DIR/infra/.env.prod"
fi
sed -i "s|BRIDGE_REDIRECT_URL=.*|BRIDGE_REDIRECT_URL=https://$DOMAIN/banking/callback|" "$DEPLOY_DIR/infra/.env.prod"

# Restart backend with updated env
$COMPOSE up -d backend
echo "✓ Backend restarted with updated CORS and Bridge redirect URL"
