#!/bin/bash
# Usage: ./scripts/db-backup.sh
# Creates a timestamped PostgreSQL dump in ./backups/

set -e

HOME=$HOME
CONTAINER_NAME="cognologix-fpa-system-db-1"   # adjust if your container name differs
DB_NAME="cognologix_fpa"
DB_USER="fpa_user"
BACKUP_DIR="$HOME/backups"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="$BACKUP_DIR/cognologix_fpa_$TIMESTAMP.sql.gz"

mkdir -p "$BACKUP_DIR"

echo "Starting backup of $DB_NAME..."
docker exec "$CONTAINER_NAME" pg_dump -U "$DB_USER" "$DB_NAME" | gzip > "$BACKUP_FILE"
echo "Backup complete: $BACKUP_FILE"
echo "Size: $(du -h "$BACKUP_FILE" | cut -f1)"
