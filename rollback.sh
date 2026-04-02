#!/bin/bash

# Configuration
BACKUP_DIR="../backups"
LATEST_BACKUP="$BACKUP_DIR/latest"

# Helper function for user confirmation
confirm() {
    local prompt="$1"
    read -p "$prompt (y/n): " response
    case "$response" in
        [yY][eE][sS]|[yY]) 
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

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
if confirm "📥 Do you want to rollback the application code (Git)?"; then
    if [ -f "$BACKUP_PATH/git_commit.txt" ]; then
        PREV_COMMIT=$(cat "$BACKUP_PATH/git_commit.txt")
        echo "📥 Reverting code to commit: $PREV_COMMIT"
        git checkout "$PREV_COMMIT"
    else
        echo "⚠️ Warning: git_commit.txt not found. Keeping current code."
    fi
else
    echo "⏭️ Skipping code rollback."
fi

# 2. Revert Uploads
if confirm "📦 Do you want to restore the uploads folder?"; then
    if [ -f "$BACKUP_PATH/uploads_backup.tar.gz" ]; then
        echo "📦 Restoring uploads folder..."
        rm -rf ./uploads
        tar -xzf "$BACKUP_PATH/uploads_backup.tar.gz"
    else
        echo "⚠️ No uploads backup found."
    fi
else
    echo "⏭️ Skipping uploads restoration."
fi

# 3. Build and Start Application
if confirm "🏗️ Do you want to restore the previous Docker image?"; then
    echo "🏗️ Restoring previous version..."
    BACKUP_NAME=$(basename "$BACKUP_PATH")
    export APP_IMAGE_TAG="$BACKUP_NAME"
    
    # Try to start using the tagged image
    docker compose up -d
    
    if [ $? -ne 0 ]; then
        echo "⚠️ Warning: Could not find tagged image $BACKUP_NAME. Rebuilding..."
        docker compose up -d --build
    else
        echo "✅ Previous Docker image restored."
        # Also tag it as latest
        docker tag ticketmanager-app:$BACKUP_NAME ticketmanager-app:latest
    fi
else
    echo "⏭️ Skipping application restoration."
fi

# 4. Restore Database
if confirm "🗄️ Do you want to restore the database?"; then
    # We wait a few seconds for the database container to be ready if it was just started
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
else
    echo "⏭️ Skipping database restoration."
fi

echo "----------------------------------------------------"
echo "  ✅ Rollback Complete!"
echo "  Application restored to previous state."
echo "----------------------------------------------------"
