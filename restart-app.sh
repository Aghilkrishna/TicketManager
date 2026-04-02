#!/bin/bash

# Navigate to the project directory (optional, but recommended if running from elsewhere)
# cd /path/to/your/project

echo "Restarting TicketManager application..."

# Stops containers, then starts them again in detached mode
# --build: Ensures any new changes are built into the images
docker compose restart || (docker compose down && docker compose up -d --build)

echo "Application restarted successfully."
echo "Check container status with: docker compose ps"
