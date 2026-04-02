#!/bin/bash

# Navigate to the project directory (optional, but recommended if running from elsewhere)
# cd /path/to/your/project

echo "Starting TicketManager application in detached mode..."

# -d: Run containers in the background
# --build: Build images before starting containers
docker compose up -d --build

echo "Application started successfully."
echo "Check container status with: docker compose ps"
