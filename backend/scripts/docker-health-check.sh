#!/bin/bash
# Docker Health Check Script
# Monitors all running containers and re-restarts them if they fail

set -e

ALERT_LOG="/var/log/flowguard-docker-health.log"
WEBHOOK_URL="${SLACK_WEBHOOK_URL:-}"

log_alert() {
  local severity=$1
  local message=$2
  echo "[$(date +'%Y-%m-%d %H:%M:%S')] [$severity] $message" >> "$ALERT_LOG"
  
  if [ -n "$WEBHOOK_URL" ]; then
    curl -s -X POST "$WEBHOOK_URL" \
      -H 'Content-Type: application/json' \
      -d "{
        \"text\": \"🔴 Docker Alert [$severity]: $message\",
        \"attachments\": [{
          \"color\": \"$([ '$severity' = 'CRITICAL' ] && echo 'danger' || echo 'warning')\",
          \"text\": \"$message\n$(date)\"
        }]
      }" &
  fi
}

# Check if each container is running
check_containers() {
  echo "[$(date +'%Y-%m-%d %H:%M:%S')] Running Docker health checks..."
  
  for container in flowguard-backend flowguard-ml-service flowguard-db flowguard-redis flowguard-web; do
    if ! docker ps --filter "name=$container" --filter "status=running" -q | grep -q .; then
      log_alert "CRITICAL" "Container '$container' is NOT RUNNING"
      echo "Attempting to restart $container..."
      docker-compose restart "$container" || log_alert "CRITICAL" "Failed to restart $container"
    fi
  done
}

# Check for unhealthy containers (if health checks are configured)
check_health() {
  docker ps -a --format "table {{.Names}}\t{{.Status}}" | grep -i unhealthy && {
    unhealthy=$(docker ps -a --format "{{.Names}}" --filter "health=unhealthy")
    log_alert "CRITICAL" "Unhealthy containers detected: $unhealthy"
    for container in $unhealthy; do
      echo "Restarting unhealthy container: $container"
      docker-compose restart "$container" || true
    done
  }
}

# Monitor disk space
check_disk() {
  usage=$(docker system df | grep -A 1 "Containers space usage" | tail -1 | awk '{print int($3)}')
  total=$(docker system df | grep -A 1 "Containers space usage" | tail -1 | awk '{print int($5)}')
  
  if [ $total -gt 0 ]; then
    percent=$((usage * 100 / total))
    if [ $percent -gt 80 ]; then
      log_alert "WARNING" "Docker disk usage is ${percent}% (${usage}GB/${total}GB)"
      docker image prune -f --filter "dangling=true" || true
    fi
  fi
}

# Main checks
check_containers
check_health
check_disk

echo "Health checks completed at $(date)"
