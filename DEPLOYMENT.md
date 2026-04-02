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
3.  Edit the `.env` file with your production secrets (JWT secret, Mail credentials, etc.). **Crucially, replace the placeholder database credentials with your own secure values.**
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

### Step 5: Nginx & SSL for yubix.tech

1.  **Configure your Domain:**
    Ensure `yubix.tech` and `www.yubix.tech` point to your VPS IP address in your DNS provider's settings.

2.  **Start the infrastructure:**
    ```bash
    sudo docker compose up -d
    ```
    At this stage, `http://yubix.tech` should be working through the Nginx container.

3.  **Obtain SSL Certificate using Certbot:**
    We recommend using Certbot on the host to manage certificates easily:
    ```bash
    sudo apt update
    sudo apt install certbot -y
    sudo certbot certonly --webroot -w ./nginx/certbot -d yubix.tech -d www.yubix.tech
    ```
    *Note: If you already have Nginx installed on the host, you might need to stop it or use the webroot method pointing to the shared volume.*

4.  **Enable HTTPS in Nginx:**
    Once certificates are obtained, edit `nginx/default.conf` to uncomment the HTTPS section:
    ```bash
    nano nginx/default.conf
    ```
    Uncomment the `server` block for port 443 and ensure the paths to `fullchain.pem` and `privkey.pem` are correct.

5.  **Restart Nginx container:**
    ```bash
    sudo docker compose restart nginx
    ```

### Step 6: Update Application URL
6. Update the `APP_BASE_URL` in your `.env` file to use `https://`:
    ```bash
    APP_BASE_URL=https://yubix.tech
    ```
7. Restart the application:
    ```bash
    sudo docker compose up -d
    ```

## Maintenance

- **Stopping the app:** `sudo docker compose down`
- **Restarting the app:** `sudo docker compose restart`
- **Updating the app:** 
  ```bash
  git pull
  sudo docker compose up -d --build
  ```
