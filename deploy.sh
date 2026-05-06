#!/bin/bash
set -euo pipefail

# Configuration
BACKUP_DIR="../backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
CURRENT_BACKUP="$BACKUP_DIR/backup_$TIMESTAMP"
ENV_FILE=".env"

# Load production environment values if present
if [ -f "$ENV_FILE" ]; then
    set -a
    source "$ENV_FILE"
    set +a
fi

DB_CONTAINER_NAME="${DB_CONTAINER_NAME:-ticketmanager-db}"
DB_NAME="${DB_NAME:-ticketmanager}"
DB_USERNAME="${DB_USERNAME:-postgres}"
APP_IMAGE_NAME="${APP_IMAGE_NAME:-ticketmanager-app}"
UPLOADS_HOST_DIR="${UPLOADS_HOST_DIR:-/opt/ticket_manager_app/uploads}"

# Create backup directory if it doesn't exist
mkdir -p "$BACKUP_DIR"

echo "----------------------------------------------------"
echo "  🚀 Starting Deployment to Production"
echo "----------------------------------------------------"

# 0. Docker Status Check
echo "Checking Docker container status..."
if docker compose ps | grep -q "Up"; then
    echo "ℹ️  Application is currently running. Proceeding with update..."
else
    echo "ℹ️  Application is currently stopped. Starting a fresh deployment..."
fi

# 1. Branch Selection
echo "Fetching latest branches from remote..."
git fetch --all --prune

echo "Select the branch you want to deploy:"
branches=($(git branch -r | sed 's/origin\///' | grep -v 'HEAD'))

for i in "${!branches[@]}"; do
    printf "[%d] %s\n" "$i" "${branches[$i]}"
done

read -p "Enter the number of the branch: " branch_idx

if [[ -z "$branch_idx" || ! "$branch_idx" =~ ^[0-9]+$ || $branch_idx -ge ${#branches[@]} ]]; then
    echo "❌ Invalid selection. Exiting."
    exit 1
fi

SELECTED_BRANCH="${branches[$branch_idx]}"
echo "✅ Selected branch: $SELECTED_BRANCH"

# 2. Pre-deployment Backup
echo "📦 Creating pre-deployment backup..."
mkdir -p "$CURRENT_BACKUP"

# Database Backup
echo "  - Backing up database..."
DUMP_FILE="$CURRENT_BACKUP/db_dump.sql"
DUMP_TMP_FILE="$CURRENT_BACKUP/db_dump.sql.tmp"

if docker exec -e PGPASSWORD="${DB_PASSWORD:-}" "$DB_CONTAINER_NAME" \
    pg_dump --clean --if-exists --create --inserts --column-inserts --verbose \
    -U "$DB_USERNAME" "$DB_NAME" > "$DUMP_TMP_FILE"; then
    if [ -s "$DUMP_TMP_FILE" ]; then
        mv "$DUMP_TMP_FILE" "$DUMP_FILE"
        echo "  - Database backup complete: $DUMP_FILE"
    else
        rm -f "$DUMP_TMP_FILE"
        echo "  ❌ Database backup failed: dump file is empty."
        exit 1
    fi
else
    rm -f "$DUMP_TMP_FILE"
    echo "  ❌ Database backup failed (check DB credentials/container)."
    exit 1
fi

# Uploads Backup
echo "  - Backing up uploads folder..."
if [ -d "$UPLOADS_HOST_DIR" ]; then
    tar -czf "$CURRENT_BACKUP/uploads_backup.tar.gz" -C "$(dirname "$UPLOADS_HOST_DIR")" "$(basename "$UPLOADS_HOST_DIR")"
    echo "  - Uploads backup complete."
else
    echo "  - Uploads folder not found at $UPLOADS_HOST_DIR, skipping."
fi

# Save current git commit hash for reference
git rev-parse HEAD > "$CURRENT_BACKUP/git_commit.txt"

# Link this as the 'latest' backup for easy rollback
ln -snf "backup_$TIMESTAMP" "$BACKUP_DIR/latest"

# 3. Pull Latest Code
echo "📥 Updating code from git..."
git checkout "$SELECTED_BRANCH"
git pull origin "$SELECTED_BRANCH"

# 4. Build and Start
echo "🏗️ Building and starting containers..."
# Using timestamp for easy identification/rollback
IMAGE_TAG="backup_$TIMESTAMP"
export APP_IMAGE_TAG="$IMAGE_TAG"

# Build the image with the specific tag
docker compose build app

# Tag it as latest for general use
docker tag "$APP_IMAGE_NAME:$IMAGE_TAG" "$APP_IMAGE_NAME:latest"

# Start with the specific tag
docker compose up -d

if [ $? -eq 0 ]; then
    echo "----------------------------------------------------"
    echo "  ✅ Deployment Successful!"
    echo "  Backup saved in: $CURRENT_BACKUP"
    echo "  To rollback, run: ./rollback.sh"
    echo "----------------------------------------------------"
else
    echo "----------------------------------------------------"
    echo "  ❌ Deployment Failed!"
    echo "  Please check the logs: docker compose logs"
    echo "  To restore previous state, run: ./rollback.sh"
    echo "----------------------------------------------------"
    exit 1
fi
