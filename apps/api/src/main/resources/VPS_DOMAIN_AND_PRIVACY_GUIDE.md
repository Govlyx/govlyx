# Govlyx Domain & Privacy Guide

This document explains how to connect your Hostinger domain to your VPS, and how to lock down the website with a password before your official launch.

---

## 1. Connect Domain via Hostinger (DNS Setup)
To connect `govlyx.com` to your server, you need to add three **A Records** in your Hostinger Domain Dashboard.

1. Log into your **Hostinger Dashboard** and go to **Domains -> govlyx.com -> DNS / Nameservers**.
2. Scroll to **Manage DNS records** and create the following three records:

| Type | Name | Points to | TTL | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **A** | `@` | `YOUR_VPS_IP` | Default | Main Website (govlyx.com) |
| **A** | `api` | `YOUR_VPS_IP` | Default | Backend API (api.govlyx.com) |
| **A** | `www` | `YOUR_VPS_IP` | Default | WWW Redirect (www.govlyx.com) |

*Note: DNS changes (Propagation) can take anywhere from 15 minutes to 24 hours to update globally across the internet.*

---

## 2. Password Protect the Website (Pre-Launch Beta)
If you want to test the live website but hide it from the public until you officially launch, you can use Nginx Basic Authentication. This forces a browser popup asking for a username and password before the site even loads.

### Step 1: Install the password tool on your VPS
```bash
sudo apt install apache2-utils -y
```

### Step 2: Create a Username & Password
Run the command below (replace `madhav` with your chosen username). It will ask you to type a password.
```bash
sudo htpasswd -c /etc/nginx/.htpasswd madhav
```

### Step 3: Add the lock to Nginx
Open your Nginx configuration file:
```bash
sudo nano /etc/nginx/sites-available/govlyx
```
Find your **Frontend Server block** (the first one for `govlyx.com`), and add these two lines right under `server_name`:
```nginx
server {
    listen 80;
    server_name govlyx.com www.govlyx.com;

    # Add these two lines to lock the site:
    auth_basic "Govlyx Private Beta";
    auth_basic_user_file /etc/nginx/.htpasswd;

    root /var/www/govlyx-frontend;
    index index.html;
    # ...
```
Save the file (`Ctrl + O`, `Enter`, `Ctrl + X`).

### Step 4: Restart Nginx
```bash
sudo systemctl restart nginx
```

> [!TIP]
> **How to make the site public again:** 
> When you are ready for your official launch, simply delete those two `auth_basic` lines from the Nginx file and restart Nginx. The site will instantly become public to the world!
