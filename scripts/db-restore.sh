#!/bin/bash
# Usage: ./scripts/db-restore.sh ./backups/cognologix_fpa_20260715_120000.sql.gz
# WARNING: This will DROP and recreate the database — all existing data will be lost.

set -e

CONTAINER_NAME="cognologix-fpa-system-db-1"
DB_NAME="cognologix_fpa"
DB_USER="fpa_user"
BACKUP_FILE="$1"

if [ -z "$BACKUP_FILE" ]; then
    echo "Usage: $0 <backup_file.sql.gz>"
    exit 1
fi

if [ ! -f "$BACKUP_FILE" ]; then
    echo "Error: Backup file not found: $BACKUP_FILE"
    exit 1
fi

echo "WARNING: This will destroy all data in $DB_NAME and restore from $BACKUP_FILE"
read -p "Type 'yes' to confirm: " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    echo "Aborted."
    exit 0
fi

echo "Dropping and recreating database..."
docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d postgres -c "DROP DATABASE IF EXISTS $DB_NAME;"
docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d postgres -c "CREATE DATABASE $DB_NAME;"

echo "Restoring from $BACKUP_FILE..."
gunzip -c "$BACKUP_FILE" | docker exec -i "$CONTAINER_NAME" psql -U "$DB_USER" "$DB_NAME"

echo "Restore complete. Restart the backend to run any pending Flyway migrations."
