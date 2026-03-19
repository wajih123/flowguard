#!/bin/powershell
# FlowGuard — Quick SSH Commands for 502 Troubleshooting
# Run these commands on your Windows PowerShell to diagnose the Hetzner server

# ============================================================
# SETUP: Save server credentials
# ============================================================
$server_ip = "157.180.43.233"
$server_user = "root"
$server_pass = "W7hKFbLCUgaCqvjmueaa3"

# Install sshpass if not already installed (for WSL/Git Bash)
# winget install sshpass

# ============================================================
# QUICK COMMANDS
# ============================================================

# 1. Basic SSH connection test
# ssh root@157.180.43.233
# Password: W7hKFbLCUgaCqvjmueaa3

# 2. Check Docker containers (use Git Bash or WSL)
# sshpass -p "W7hKFbLCUgaCqvjmueaa3" ssh -o StrictHostKeyChecking=no root@157.180.43.233 "docker ps -a"

# 3. Check if docker-compose stack is deployed
# sshpass -p "W7hKFbLCUgaCqvjmueaa3" ssh root@157.180.43.233 "cd /root && docker compose ps"

# 4. View nginx error logs
# sshpass -p "W7hKFbLCUgaCqvjmueaa3" ssh root@157.180.43.233 "docker logs nginx 2>&1 | tail -30"

# 5. View backend logs
# sshpass -p "W7hKFbLCUgaCqvjmueaa3" ssh root@157.180.43.233 "docker logs backend 2>&1 | tail -30"

# 6. Check if backend is responding
# sshpass -p "W7hKFbLCUgaCqvjmueaa3" ssh root@157.180.43.233 "curl -s http://localhost:8080/health"

# 7. Restart all services
# sshpass -p "W7hKFbLCUgaCqvjmueaa3" ssh root@157.180.43.233 "cd /root && docker compose down && docker compose up -d"

# ============================================================
# TROUBLESHOOTING CHECKLIST
# ============================================================

<# 
COMMON 502 CAUSES:

[_] 1. Docker containers not running
    → Check: sshpass -p "W7hKFbLCUgaCqvjmueaa3" ssh root@157.180.43.233 "docker ps -a"
    → Fix: docker compose up -d

[_] 2. Backend service crashed
    → Check: docker logs backend | grep -i "error"
    → Check environment variables are set

[_] 3. PostgreSQL or Redis not running
    → Check: docker ps | grep postgres
    → Check: docker ps | grep redis

[_] 4. Networks not connected
    → Check: docker network ls
    → Fix: docker compose down && docker compose up -d

[_] 5. Environment variables missing
    → Check current: docker exec backend env | grep DB_
    → Check .env file: cat /root/.env

[_] 6. DNS resolution failing
    → Test: docker exec nginx curl -v http://backend:8080
#>

# ============================================================
# RECOMMENDED FIXES
# ============================================================

# Fix 1: Full restart (safest first step)
# sshpass -p "W7hKFbLCUgaCqvjmueaa3" ssh root@157.180.43.233 << 'EOF'
# cd /root
# docker compose down
# sleep 3
# docker compose up -d
# sleep 10
# docker compose ps
# EOF

# Fix 2: Check backend health immediately (after 30s startup)
# sshpass -p "W7hKFbLCUgaCqvjmueaa3" ssh root@157.180.43.233 "sleep 30 && curl -s http://localhost:8080/health"

# Fix 3: View all logs simultaneously
# sshpass -p "W7hKFbLCUgaCqvjmueaa3" ssh root@157.180.43.233 << 'EOF'
# echo "=== NGINX ===" && docker logs nginx --tail 20 && \
# echo "=== BACKEND ===" && docker logs backend --tail 20 && \
# echo "=== POSTGRES ===" && docker logs postgres --tail 5
# EOF
