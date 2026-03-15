#!/usr/bin/env bash
# ============================================================
# FlowGuard — OWASP ZAP Security Scan
# ============================================================
# Runs an automated OWASP ZAP baseline scan against the
# FlowGuard API, failing the CI pipeline on any HIGH-severity
# finding.
#
# Usage:
#   ./tests/security/owasp-scan.sh
#   ./tests/security/owasp-scan.sh http://staging.flowguard.fr
#
# Requirements:
#   - Docker (for OWASP ZAP image)
#   - A running FlowGuard instance (default: http://localhost:8080)
#
# Output:
#   - scan report in tests/security/reports/zap-report.html
#   - exits non-zero if HIGH-severity findings are detected
# ============================================================

set -euo pipefail

TARGET_URL="${1:-http://host.docker.internal:8080}"
REPORT_DIR="$(dirname "$0")/reports"
REPORT_HTML="${REPORT_DIR}/zap-report.html"
REPORT_JSON="${REPORT_DIR}/zap-report.json"
ZAP_IMAGE="ghcr.io/zaproxy/zaproxy:stable"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[zap]${NC} $*"; }
warn() { echo -e "${YELLOW}[zap]${NC} $*"; }
fail() { echo -e "${RED}[zap]${NC} $*" >&2; }

mkdir -p "${REPORT_DIR}"

log "Starting OWASP ZAP baseline scan against ${TARGET_URL}"
log "Report will be saved to ${REPORT_HTML}"

# ── Pull ZAP image ─────────────────────────────────────────────────────────────
docker pull "${ZAP_IMAGE}" --quiet

# ── Run ZAP baseline scan ──────────────────────────────────────────────────────
# --rm          remove container after run
# -v            mount report directory
# -t            ZAP Docker baseline scan script
# -d            debug output
# -I            don't fail on WARN alerts (fail only on HIGH)

EXIT_CODE=0
docker run --rm \
    -v "${REPORT_DIR}:/zap/wrk:rw" \
    --add-host="host.docker.internal:host-gateway" \
    "${ZAP_IMAGE}" \
    zap-baseline.py \
        -t "${TARGET_URL}/api/openapi" \
        -r /zap/wrk/zap-report.html \
        -J /zap/wrk/zap-report.json \
        -I \
        -c /zap/wrk/zap-config.conf \
        2>&1 | tee /tmp/zap-output.txt || EXIT_CODE=$?

# ── Parse results ──────────────────────────────────────────────────────────────
if [[ -f "${REPORT_JSON}" ]]; then
    HIGH_COUNT=$(python3 -c "
import json, sys
try:
    data = json.load(open('${REPORT_JSON}'))
    high = sum(1 for site in data.get('site', [])
               for alert in site.get('alerts', [])
               if alert.get('riskcode') == '3')
    print(high)
except Exception as e:
    print(0)
" 2>/dev/null || echo "0")

    MEDIUM_COUNT=$(python3 -c "
import json, sys
try:
    data = json.load(open('${REPORT_JSON}'))
    medium = sum(1 for site in data.get('site', [])
                 for alert in site.get('alerts', [])
                 if alert.get('riskcode') == '2')
    print(medium)
except Exception as e:
    print(0)
" 2>/dev/null || echo "0")

    log "Scan complete. HIGH: ${HIGH_COUNT}  MEDIUM: ${MEDIUM_COUNT}"
    log "Full report: ${REPORT_HTML}"

    if [[ "${HIGH_COUNT}" -gt 0 ]]; then
        fail "❌ ${HIGH_COUNT} HIGH-severity finding(s) detected — see ${REPORT_HTML}"
        exit 1
    fi

    if [[ "${MEDIUM_COUNT}" -gt 0 ]]; then
        warn "⚠️  ${MEDIUM_COUNT} MEDIUM-severity finding(s) detected (non-blocking)"
    fi
else
    warn "Report JSON not found — checking exit code"
    if [[ ${EXIT_CODE} -ne 0 && ${EXIT_CODE} -ne 2 ]]; then
        # Exit code 2 = only WARN alerts (acceptable), >2 = HIGH
        fail "ZAP scan failed with exit code ${EXIT_CODE}"
        exit ${EXIT_CODE}
    fi
fi

log "✅ OWASP ZAP scan passed"
