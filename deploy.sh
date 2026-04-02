#!/bin/bash

# Configuration
BACKUP_DIR="./backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
CURRENT_BACKUP="$BACKUP_DIR/backup_$TIMESTAMP"

# Create backup directory if it doesn't exist
mkdir -p "$BACKUP_DIR"

echo "----------------------------------------------------"
echo "  🚀 Starting Deployment to Production"
echo "----------------------------------------------------"

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
docker exec ticketmanager-db pg_dump -U postgres ticketmanager > "$CURRENT_BACKUP/db_dump.sql" 2>/dev/null
if [ $? -eq 0 ]; then
    echo "  - Database backup complete."
else
    echo "  ⚠️ Warning: Database backup failed (is the DB container running?)."
fi

# Uploads Backup
echo "  - Backing up uploads folder..."
if [ -d "./uploads" ]; then
    tar -czf "$CURRENT_BACKUP/uploads_backup.tar.gz" ./uploads
    echo "  - Uploads backup complete."
else
    echo "  - No uploads folder found, skipping."
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
# Using --build to ensure code changes are captured
docker compose up -d --build

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
