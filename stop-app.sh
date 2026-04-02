#!/bin/bash

# Navigate to the project directory (optional, but recommended if running from elsewhere)
# cd /path/to/your/project

echo "Stopping TicketManager application containers..."

# Stops containers and removes containers, networks, images, and volumes 
# defined in the docker-compose.yml file.
# Note: This does NOT delete the persistent data in your volumes.
docker compose down

echo "Containers stopped successfully."
