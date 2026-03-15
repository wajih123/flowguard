#!/bin/bash
echo "=== BACKEND ERRORS/WARNINGS (excl. known noise) ==="
docker logs flowguard-backend 2>&1 | grep -E 'ERROR|WARN' | grep -v 'RedisHealthCheck\|SRHCK\|Reporting health'

echo ""
echo "=== BACKEND: last exception with context ==="
docker logs flowguard-backend 2>&1 | grep -A 5 'Exception\|Caused by' | grep -v 'at io\.\|at java\.\|at com\.sun' | head -60

echo ""
echo "=== ML SERVICE ERRORS ==="
docker logs flowguard-ml 2>&1 | grep -E 'ERROR|error|Error|Exception|traceback' | head -20

echo ""
echo "=== NGINX ERRORS ==="
docker logs flowguard-nginx 2>&1 | grep -E 'error\|emerg\|crit' | tail -20

echo ""
echo "=== BACKEND startup summary ==="
docker logs flowguard-backend 2>&1 | grep -E 'started|Profile|Installed|flyway|Flyway|migration' | head -20
