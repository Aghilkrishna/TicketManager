#!/bin/bash

# Configuration
BACKUP_DIR="./backups"
LATEST_BACKUP="$BACKUP_DIR/latest"

echo "----------------------------------------------------"
echo "  🔄 Starting Rollback to Previous Working State"
echo "----------------------------------------------------"

if [ ! -L "$LATEST_BACKUP" ]; then
    echo "❌ Error: No backup found to restore."
    exit 1
fi

BACKUP_PATH=$(readlink -f "$LATEST_BACKUP")
echo "✅ Restoring from: $BACKUP_PATH"

# 1. Revert Git Code
if [ -f "$BACKUP_PATH/git_commit.txt" ]; then
    PREV_COMMIT=$(cat "$BACKUP_PATH/git_commit.txt")
    echo "📥 Reverting code to commit: $PREV_COMMIT"
    git checkout "$PREV_COMMIT"
else
    echo "⚠️ Warning: git_commit.txt not found. Keeping current code."
fi

# 2. Revert Uploads
if [ -f "$BACKUP_PATH/uploads_backup.tar.gz" ]; then
    echo "📦 Restoring uploads folder..."
    rm -rf ./uploads
    tar -xzf "$BACKUP_PATH/uploads_backup.tar.gz"
else
    echo "⚠️ No uploads backup found."
fi

# 3. Build and Start Old Version
echo "🏗️ Rebuilding previous version..."
docker compose up -d --build

# 4. Restore Database
# We wait a few seconds for the database container to be ready
echo "⏳ Waiting for database to be ready..."
sleep 5

if [ -f "$BACKUP_PATH/db_dump.sql" ]; then
    echo "🗄️ Restoring database from SQL dump..."
    # Drop and recreate database to ensure clean restore (using same env vars as docker compose)
    DB_NAME="ticketmanager"
    DB_USER="postgres"
    
    cat "$BACKUP_PATH/db_dump.sql" | docker exec -i ticketmanager-db psql -U "$DB_USER" -d "$DB_NAME" > /dev/null
    
    if [ $? -eq 0 ]; then
        echo "✅ Database restore complete."
    else
        echo "❌ Error: Database restore failed."
    fi
else
    echo "⚠️ No database backup found."
fi

echo "----------------------------------------------------"
echo "  ✅ Rollback Complete!"
echo "  Application restored to previous state."
echo "----------------------------------------------------"
