#!/usr/bin/env bash
set -euo pipefail
# Nightly backup: postgres dump + minio mirror. Wire to cron on the VM.
BACKUP_DIR=${BACKUP_DIR:-/var/backups/etg}
mkdir -p "$BACKUP_DIR"
TS=$(date +%F-%H%M)
docker compose exec -T db pg_dump -U "${POSTGRES_USER:-etg}" "${POSTGRES_DB:-etg}" | gzip > "$BACKUP_DIR/etg-$TS.sql.gz"
echo "backup written to $BACKUP_DIR/etg-$TS.sql.gz"
