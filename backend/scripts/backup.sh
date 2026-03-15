#!/usr/bin/env bash
# ============================================================
# FlowGuard — PostgreSQL Backup to Hetzner Object Storage
# ============================================================
# Usage:
#   ./scripts/backup.sh                  # Full backup
#   ./scripts/backup.sh --dry-run        # Print what would be done
#
# Required env vars:
#   DB_HOST, DB_PORT, DB_USER, DB_PASSWORD, DB_NAME
#   S3_BUCKET          — e.g. "flowguard-backups"
#   S3_ENDPOINT        — Hetzner S3 endpoint, e.g. "https://fsn1.your-objectstorage.com"
#   S3_ACCESS_KEY      — Hetzner Object Storage access key
#   S3_SECRET_KEY      — Hetzner Object Storage secret key
#   BACKUP_GPG_KEY_ID  — (optional) GPG key fingerprint for encryption
#
# Dependencies: pg_dump, gzip, gpg (optional), s3cmd or AWS CLI
# ============================================================

set -euo pipefail

# ── Configuration ─────────────────────────────────────────────────────────────
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-flowguard}"
DB_NAME="${DB_NAME:-flowguard}"
S3_BUCKET="${S3_BUCKET:-flowguard-backups}"
S3_ENDPOINT="${S3_ENDPOINT:-}"
BACKUP_DIR="/tmp/flowguard-backup"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="flowguard_${TIMESTAMP}.sql.gz"
BACKUP_PATH="${BACKUP_DIR}/${BACKUP_FILE}"
DRY_RUN="${1:-}"

# ── Colours ───────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log()  { echo -e "${GREEN}[backup]${NC} $*"; }
warn() { echo -e "${YELLOW}[backup]${NC} $*"; }
fail() { echo -e "${RED}[backup]${NC} $*" >&2; exit 1; }

# ── Preflight checks ──────────────────────────────────────────────────────────
command -v pg_dump  >/dev/null 2>&1 || fail "pg_dump not found"
command -v gzip     >/dev/null 2>&1 || fail "gzip not found"

if [[ -n "${S3_ENDPOINT}" ]]; then
    command -v s3cmd >/dev/null 2>&1 \
        || command -v aws >/dev/null 2>&1 \
        || fail "Neither s3cmd nor aws CLI found"
fi

[[ -z "${DB_PASSWORD:-}" ]] && fail "DB_PASSWORD env var not set"

# ── Functions ──────────────────────────────────────────────────────────────────

cleanup() {
    log "Cleaning up temp files"
    rm -rf "${BACKUP_DIR}"
}
trap cleanup EXIT

create_backup() {
    log "Creating backup directory: ${BACKUP_DIR}"
    mkdir -p "${BACKUP_DIR}"

    log "Dumping database '${DB_NAME}' from ${DB_HOST}:${DB_PORT}..."
    PGPASSWORD="${DB_PASSWORD}" pg_dump \
        --host="${DB_HOST}" \
        --port="${DB_PORT}" \
        --username="${DB_USER}" \
        --format=plain \
        --no-password \
        --verbose \
        "${DB_NAME}" \
        | gzip -9 > "${BACKUP_PATH}"

    local size
    size=$(du -sh "${BACKUP_PATH}" | cut -f1)
    log "Backup created: ${BACKUP_FILE} (${size})"
}

encrypt_backup() {
    if [[ -z "${BACKUP_GPG_KEY_ID:-}" ]]; then
        warn "BACKUP_GPG_KEY_ID not set — skipping GPG encryption"
        return
    fi

    log "Encrypting backup with GPG key ${BACKUP_GPG_KEY_ID}..."
    gpg --batch --yes \
        --recipient "${BACKUP_GPG_KEY_ID}" \
        --output "${BACKUP_PATH}.gpg" \
        --encrypt "${BACKUP_PATH}"

    rm -f "${BACKUP_PATH}"
    BACKUP_FILE="${BACKUP_FILE}.gpg"
    BACKUP_PATH="${BACKUP_PATH}.gpg"
    log "Encrypted: ${BACKUP_FILE}"
}

upload_to_s3() {
    if [[ -z "${S3_ENDPOINT}" ]]; then
        warn "S3_ENDPOINT not set — skipping upload (local backup only)"
        log "Local backup available at: ${BACKUP_PATH}"
        return
    fi

    local s3_path="s3://${S3_BUCKET}/backups/${BACKUP_FILE}"
    log "Uploading to ${s3_path}..."

    if command -v s3cmd >/dev/null 2>&1; then
        s3cmd put \
            --access_key="${S3_ACCESS_KEY}" \
            --secret_key="${S3_SECRET_KEY}" \
            --host="${S3_ENDPOINT#https://}" \
            --host-bucket="%(bucket)s.${S3_ENDPOINT#https://}" \
            --ssl \
            --server-side-encryption \
            "${BACKUP_PATH}" \
            "${s3_path}"
    else
        AWS_ACCESS_KEY_ID="${S3_ACCESS_KEY}" \
        AWS_SECRET_ACCESS_KEY="${S3_SECRET_KEY}" \
        aws s3 cp \
            --endpoint-url="${S3_ENDPOINT}" \
            "${BACKUP_PATH}" \
            "${s3_path}"
    fi

    log "Upload complete: ${s3_path}"
}

rotate_old_backups() {
    if [[ -z "${S3_ENDPOINT}" ]]; then return; fi

    log "Rotating old backups (keeping last 30 days)..."
    local cutoff
    cutoff=$(date -d "30 days ago" +%Y%m%d 2>/dev/null || date -v-30d +%Y%m%d)

    if command -v s3cmd >/dev/null 2>&1; then
        s3cmd ls \
            --access_key="${S3_ACCESS_KEY}" \
            --secret_key="${S3_SECRET_KEY}" \
            --host="${S3_ENDPOINT#https://}" \
            "s3://${S3_BUCKET}/backups/" \
        | awk '{print $4}' \
        | while read -r obj; do
            local file_date
            file_date=$(basename "${obj}" | grep -oP '\d{8}' | head -1 || true)
            if [[ -n "${file_date}" && "${file_date}" < "${cutoff}" ]]; then
                log "Deleting old backup: ${obj}"
                s3cmd del \
                    --access_key="${S3_ACCESS_KEY}" \
                    --secret_key="${S3_SECRET_KEY}" \
                    --host="${S3_ENDPOINT#https://}" \
                    "${obj}" || warn "Failed to delete ${obj}"
            fi
        done
    fi
}

notify_success() {
    log "✅ Backup completed successfully: ${BACKUP_FILE}"
    # Optionally send a notification (Slack webhook, email, etc.)
    if [[ -n "${BACKUP_SLACK_WEBHOOK:-}" ]]; then
        curl -s -X POST "${BACKUP_SLACK_WEBHOOK}" \
            -H 'Content-type: application/json' \
            --data "{\"text\":\"✅ FlowGuard backup completed: \`${BACKUP_FILE}\`\"}" \
            || warn "Slack notification failed"
    fi
}

# ── Main ───────────────────────────────────────────────────────────────────────
log "FlowGuard backup starting at $(date)"

if [[ "${DRY_RUN}" == "--dry-run" ]]; then
    log "[DRY RUN] Would back up ${DB_NAME} → ${S3_BUCKET}/backups/${BACKUP_FILE}"
    exit 0
fi

create_backup
encrypt_backup
upload_to_s3
rotate_old_backups
notify_success
