# Govlyx VPS Deployment Guide

This document contains every step required to deploy the Govlyx / Govlyx application (Spring Boot Backend + React/Vite Frontend) to an Ubuntu VPS.

---

## 1. Initial VPS Setup

Connect to your VPS via SSH from your local Windows PowerShell:

```powershell
ssh root@YOUR_VPS_IP
```

Update Ubuntu and install the required software (Java 21 and Nginx):

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install openjdk-21-jdk -y
sudo apt install nginx -y
```

---

## 2. Build & Upload the Backend

On your **Local Windows PC**, open PowerShell. You can run these commands from any directory because they use absolute paths.

1. Compile the Spring Boot application into a `.jar` file:

```powershell
mvn -f "c:\Users\Madhav\Desktop\Springboot project\AI\AI\pom.xml" clean package "-Dmaven.test.skip=true"
```

2. Securely copy the generated `.jar` file to the VPS:

```powershell
scp "c:\Users\Madhav\Desktop\Springboot project\AI\AI\target\AI-0.0.1-SNAPSHOT.jar" root@YOUR_VPS_IP:/opt/govlyx-backend.jar
```

---

## 3. Create the Systemd Background Service

On the **VPS SSH Terminal**, create a service file so the backend runs automatically in the background and restarts on server reboots.

1. Open the service file for editing:

```bash
sudo nano /etc/systemd/system/govlyx-backend.service
```

2. Paste the following configuration:

```ini
[Unit]
Description=Govlyx Spring Boot Backend
After=network.target

[Service]
User=root
ExecStart=/usr/bin/java -jar /opt/govlyx-backend.jar
SuccessExitStatus=143
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

3. Save and close (`Ctrl + O`, `Enter`, `Ctrl + X`).

4. Enable and start the service:

```bash
sudo systemctl daemon-reload
sudo systemctl enable govlyx-backend
sudo systemctl start govlyx-backend
```

5. Verify it is running:

```bash
sudo journalctl -u govlyx-backend -f
```

---

## 4. Resolve Database Constraints (Supabase)

During the initial run, if the `AutoEscalationTask` throws a `posts_broadcast_scope_check` constraint error (because the database doesn't recognize the `NEARBY` enum), run the following in the **Supabase SQL Editor**:

```sql
ALTER TABLE posts DROP CONSTRAINT posts_broadcast_scope_check;

ALTER TABLE posts ADD CONSTRAINT posts_broadcast_scope_check
CHECK (broadcast_scope IN ('COUNTRY', 'STATE', 'DISTRICT', 'NEARBY', 'AREA'));
```

---

## 5. Build & Upload the Frontend

On your **Local Windows PC**, prepare the frontend for production:

1. Update the Frontend API URLs (`src/api/axiosConfig.ts` and `src/utils/apiUrl.ts`) to point to the new Nginx reverse proxy:

```typescript
const FALLBACK_URL = 'https://api.govlyx.com';
```

2. Open PowerShell in the frontend folder (`C:\Users\Madhav\Desktop\Govlyx`) and build the app:

```powershell
npm install
npm run build
```

3. Create the frontend directory on the **VPS**:

```bash
sudo mkdir -p /var/www/govlyx-frontend
```

4. Upload the built frontend to the VPS from **Local Windows PowerShell**:

```powershell
scp -r dist/* root@YOUR_VPS_IP:/var/www/govlyx-frontend/
```

---

## 6. Configure Nginx (The Reverse Proxy)

On the **VPS SSH Terminal**, configure Nginx to route traffic to the Frontend (Static HTML) and the Backend (Port 8081).

1. Edit the configuration file:

```bash
sudo nano /etc/nginx/sites-available/govlyx
```

2. Paste the unified configuration:

```nginx
# 1. FRONTEND SERVER (govlyx.com)
server {
    listen 80;
    server_name govlyx.com www.govlyx.com;

    root /var/www/govlyx-frontend;
    index index.html;

    # Handles React/Vite Router page refreshes
    location / {
        try_files $uri $uri/ /index.html;
    }
}

# 2. BACKEND API SERVER (api.govlyx.com)
server {
    listen 80;
    server_name api.govlyx.com;

    location / {
        proxy_pass http://localhost:8081; # Forwards to Spring Boot

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Required for WebSockets / Chat
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";

        # Allows users to upload large images/videos
        client_max_body_size 512M;
    }
}
```

3. Save and close. Then activate the configuration:

```bash
sudo ln -s /etc/nginx/sites-available/govlyx /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

---

## 7. Hostinger DNS Setup

In the **Hostinger Domain Dashboard** for `govlyx.com`, add these two A Records to route domain traffic to your VPS:

- **Name:** `@` | **Target:** `YOUR_VPS_IP`
- **Name:** `api` | **Target:** `YOUR_VPS_IP`

---

## 8. Useful Server Commands (Monitoring & Logs)

Once everything is running, you can use these commands in your **VPS SSH Terminal** to monitor your app and fix issues:

### 🚀 Backend Service Management

- **Check if the backend is running:** `sudo systemctl status govlyx-backend`
- **Stop the backend:** `sudo systemctl stop govlyx-backend`
- **Start the backend:** `sudo systemctl start govlyx-backend`
- **Restart the backend:** `sudo systemctl restart govlyx-backend`

### 📝 Viewing Backend Logs (Journalctl)

- **Live Feed (Watch logs in real-time as users use the app):**

  ```bash
  sudo journalctl -u govlyx-backend -f
  ```

  _(Press `Ctrl + C` to exit the live feed)._

- **View the last 100 lines (Quick check):**

  ```bash
  sudo journalctl -u govlyx-backend -n 100 --no-pager
  ```

- **See ONLY Errors (Filter out the noise):**

  ```bash
  sudo journalctl -u govlyx-backend -p err
  ```

- **Search for a specific word (e.g., finding payment errors):**
  ```bash
  sudo journalctl -u govlyx-backend | grep "Payment"
  ```

### 🌐 Nginx Management

- **Check Nginx status:** `sudo systemctl status nginx`
- **Restart Nginx (after changing config):** `sudo systemctl restart nginx`
- **Check Nginx for syntax errors:** `sudo nginx -t`
- **View Nginx Error Logs (If your site isn't loading):**
  ```bash
  sudo tail -f /var/log/nginx/error.log
  ```
