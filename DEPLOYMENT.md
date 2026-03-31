# Deployment Guide for TicketManager

This guide provides step-by-step instructions to deploy the TicketManager application on a VPS using Docker and Docker Compose.

## Prerequisites

1.  A VPS running a modern Linux distribution (e.g., Ubuntu 22.04 LTS).
2.  Docker and Docker Compose installed on the VPS.
3.  A domain name (optional but recommended for SSL).

## Step 1: Install Docker & Docker Compose (if not installed)

On Ubuntu:
```bash
# Update package list
sudo apt update

# Install dependencies
sudo apt install -y apt-transport-https ca-certificates curl software-properties-common

# Add Docker’s official GPG key
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

# Set up the stable repository
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io

# Install Docker Compose
sudo apt install -y docker-compose-plugin
```

## Step 2: Prepare Deployment Files

1.  Clone your repository or upload the project files to the VPS.
2.  Create a `.env` file from the `prod.env.example` template:
    ```bash
    cp prod.env.example .env
    ```
3.  Edit the `.env` file with your production secrets (JWT secret, Mail credentials, etc.):
    ```bash
    nano .env
    ```

## Step 3: Deploy with Docker Compose

Run the following command in the project root directory:
```bash
sudo docker compose up -d --build
```
This command will:
- Build the Spring Boot application image.
- Start the PostgreSQL database container.
- Start the Spring Boot application container.
- Set up the network and volumes.

## Step 4: Verification

Check the status of the containers:
```bash
sudo docker compose ps
```
View the application logs to ensure it started correctly:
```bash
sudo docker compose logs -f app
```
The application should now be accessible at `http://your-vps-ip:9090`.

## Step 5: (Optional) Nginx Reverse Proxy & SSL

For better security and performance, use Nginx as a reverse proxy.

### Install Nginx
```bash
sudo apt install nginx
```

### Configure Nginx
Create a new configuration file: `/etc/nginx/sites-available/ticketmanager`
```nginx
server {
    listen 80;
    server_name yourdomain.com;

    location / {
        proxy_pass http://localhost:9090;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # WebSocket support
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
    }
}
```
Enable the site and restart Nginx:
```bash
sudo ln -s /etc/nginx/sites-available/ticketmanager /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

### SSL with Certbot
```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d yourdomain.com
```

## Maintenance

- **Stopping the app:** `sudo docker compose down`
- **Restarting the app:** `sudo docker compose restart`
- **Updating the app:** 
  ```bash
  git pull
  sudo docker compose up -d --build
  ```
