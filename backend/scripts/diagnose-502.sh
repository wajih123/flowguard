#!/usr/bin/env bash
# ============================================================
# FlowGuard — 502 Bad Gateway Diagnostic Script
# ============================================================
# Run on the Hetzner server to diagnose nginx 502 errors
# Usage: bash diagnose-502.sh

set -u

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "FlowGuard — 502 Bad Gateway Diagnostic"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# 1. Check Docker daemon
echo -e "\n${YELLOW}[1/7]${NC} Docker daemon status..."
if ! command -v docker &> /dev/null; then
    echo -e "${RED}✗${NC} Docker not found"
    exit 1
fi
docker version --format '{{.Server.Version}}' && echo -e "${GREEN}✓${NC} Docker running" || echo -e "${RED}✗${NC} Docker not responding"

# 2. Check containers
echo -e "\n${YELLOW}[2/7]${NC} Docker containers status..."
docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 3. Check if specific services exist
echo -e "\n${YELLOW}[3/7]${NC} Critical services..."
for container in nginx backend postgres redis; do
    if docker ps -a --format "{{.Names}}" | grep -q "$container"; then
        STATUS=$(docker ps -a --filter "name=$container" --format "{{.Status}}")
        if docker ps --filter "name=$container" --format "{{.Names}}" | grep -q "$container"; then
            echo -e "${GREEN}✓${NC} $container: Running ($STATUS)"
        else
            echo -e "${RED}✗${NC} $container: Stopped ($STATUS)"
        fi
    else
        echo -e "${RED}✗${NC} $container: Not found"
    fi
done

# 4. Check nginx logs
echo -e "\n${YELLOW}[4/7]${NC} Nginx last 10 error logs..."
docker logs nginx 2>&1 | tail -20 | head -10

# 5. Check backend logs
echo -e "\n${YELLOW}[5/7]${NC} Backend last 10 logs..."
docker logs backend 2>&1 | tail -15 | head -10

# 6. Test connectivity
echo -e "\n${YELLOW}[6/7]${NC} Backend health check (from nginx container)..."
docker exec nginx curl -s -o /dev/null -w "Status: %{http_code}\n" http://backend:8080/health || echo -e "${RED}✗${NC} Cannot reach backend from nginx"

# 7. Check docker network
echo -e "\n${YELLOW}[7/7]${NC} Docker network inspection..."
NETWORK=$(docker ps --filter "name=nginx" --format "{{json .HostConfig}}" 2>/dev/null | grep -o '"[^"]*default[^"]*"' | head -1 | tr -d '"' || echo "flowguard_default")
echo "Network: $NETWORK"
docker network inspect "$NETWORK" --format "{{range .Containers}}  {{.Name}}: {{.IPv4Address}}{{println}}{{end}}" 2>/dev/null || echo -e "${YELLOW}network:${NC} not found"

echo -e "\n${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo "Common fixes:"
echo "  1. Restart all: docker compose down && docker compose up -d"
echo "  2. Check env vars: cat .env"
echo "  3. View full nginx logs: docker logs -f nginx"
echo "  4. View full backend logs: docker logs -f backend"
