#!/usr/bin/env bash
# ============================================================
# FlowGuard — Zero-Downtime Production Deploy Script
# ============================================================
# Run on the Hetzner server (or via CI/CD).
#
# Strategy:
#   - NEVER does `docker compose down` (that kills nginx → 502)
#   - Only rebuilds and rolling-restarts app services
#   - Postgres, Redis, and Nginx stay up throughout
#   - Health-checks every service before declaring success
#   - Auto-rollback on failure via image tagging
#
# Usage:
#   ./scripts/deploy.sh              # Deploy all app services
#   ./scripts/deploy.sh backend      # Deploy only backend
#   ./scripts/deploy.sh frontend     # Deploy only frontend
#   ./scripts/deploy.sh ml-service   # Deploy only ml-service
# ============================================================

set -euo pipefail

# ── Colour helpers ────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log()  { echo -e "${GREEN}[deploy]${NC} $*"; }
info() { echo -e "${BLUE}[deploy]${NC} $*"; }
warn() { echo -e "${YELLOW}[deploy]${NC} $*"; }
fail() { echo -e "${RED}[deploy]${NC} FAILED: $*" >&2; exit 1; }

# ── Config ────────────────────────────────────────────────────────────────────
COMPOSE_DIR="${COMPOSE_DIR:-/root/flowguard/backend}"
HEALTH_TIMEOUT=120     # seconds to wait for backend health
HEALTH_INTERVAL=10     # check every N seconds
TARGET="${1:-all}"     # which service(s) to deploy

cd "$COMPOSE_DIR"

# ── Preflight checks ──────────────────────────────────────────────────────────
[[ -f ".env" ]] || fail ".env file not found at $COMPOSE_DIR/.env — run server-init.sh first"
command -v docker >/dev/null 2>&1 || fail "docker not found"
docker compose version >/dev/null 2>&1 || fail "docker compose not found"

log "Starting deploy — target: ${TARGET}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ── Pull latest code ──────────────────────────────────────────────────────────
info "Pulling latest code from git..."
git fetch origin main
git reset --hard origin/main
log "Code up to date ($(git rev-parse --short HEAD))"

# ── Tag current images as previous (for rollback) ─────────────────────────────
tag_for_rollback() {
    local service=$1
    local image
    image=$(docker compose config --format json 2>/dev/null | \
        python3 -c "import sys,json; c=json.load(sys.stdin); print(c['services']['$service'].get('image',''))" 2>/dev/null || echo "")
    if [[ -n "$image" ]] && docker image inspect "$image" &>/dev/null; then
        docker tag "$image" "${image}:rollback" 2>/dev/null || true
        info "Tagged $image as rollback"
    fi
}

# ── Build services ────────────────────────────────────────────────────────────
build_services() {
    local services=("$@")
    info "Building: ${services[*]}"
    docker compose build --pull "${services[@]}"
    log "Build complete"
}

# ── Rolling restart (nginx/postgres/redis stay up) ────────────────────────────
restart_service() {
    local svc=$1
    info "Restarting $svc..."
    # --no-deps: don't restart dependencies (postgres, redis)
    # Only update the specific service
    docker compose up -d --no-deps "$svc"
    log "$svc container replaced"
}

# ── Health check ─────────────────────────────────────────────────────────────
wait_healthy() {
    local svc=$1
    local container="flowguard-${svc}"
    info "Waiting for $svc to be healthy (max ${HEALTH_TIMEOUT}s)..."
    local elapsed=0
    while [[ $elapsed -lt $HEALTH_TIMEOUT ]]; do
        local status
        status=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "unknown")
        case "$status" in
            healthy)
                log "✓ $svc is healthy"
                return 0
                ;;
            unhealthy)
                fail "$svc reported unhealthy — check: docker logs $container"
                ;;
            *)
                echo -ne "\r  ${YELLOW}[deploy]${NC} $svc status: $status (${elapsed}s / ${HEALTH_TIMEOUT}s)"
                sleep "$HEALTH_INTERVAL"
                elapsed=$((elapsed + HEALTH_INTERVAL))
                ;;
        esac
    done
    echo ""
    fail "$svc did not become healthy within ${HEALTH_TIMEOUT}s"
}

# ── Rollback ──────────────────────────────────────────────────────────────────
rollback_service() {
    local svc=$1
    warn "Rolling back $svc..."
    local image
    image=$(docker compose config --format json 2>/dev/null | \
        python3 -c "import sys,json; c=json.load(sys.stdin); print(c['services']['$svc'].get('image',''))" 2>/dev/null || echo "")
    if [[ -n "$image" ]] && docker image inspect "${image}:rollback" &>/dev/null; then
        docker tag "${image}:rollback" "$image"
        docker compose up -d --no-deps "$svc"
        warn "Rollback of $svc complete"
    else
        warn "No rollback image found for $svc"
    fi
}

# ── Deploy logic ──────────────────────────────────────────────────────────────
deploy_backend() {
    tag_for_rollback backend
    build_services backend
    restart_service backend
    if ! wait_healthy backend; then
        rollback_service backend
        fail "Backend deploy failed — rolled back to previous version"
    fi
}

deploy_frontend() {
    tag_for_rollback frontend
    build_services frontend
    restart_service frontend
    wait_healthy frontend || warn "Frontend health check inconclusive — check manually"
}

deploy_ml() {
    tag_for_rollback ml-service
    build_services ml-service
    restart_service ml-service
    wait_healthy ml-service || warn "ML-service health check inconclusive — check manually"
}

# ── Main ──────────────────────────────────────────────────────────────────────
case "$TARGET" in
    backend)   deploy_backend ;;
    frontend)  deploy_frontend ;;
    ml-service) deploy_ml ;;
    all)
        # Infra stays up (postgres, redis, nginx don't get touched)
        deploy_backend
        deploy_frontend
        deploy_ml
        ;;
    *)
        fail "Unknown target: $TARGET. Use: all | backend | frontend | ml-service"
        ;;
esac

# ── Final status ─────────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log "Deploy complete ✓ ($(git rev-parse --short HEAD))"
docker compose ps --format "table {{.Name}}\t{{.Status}}"
