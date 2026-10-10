# Govlyx VPS Security Guide

This document contains the security hardening steps required for your Ubuntu VPS running Govlyx.

> [!TIP]
> **Unbanning an IP Address:** If you (or a legitimate user) ever get locked out by Fail2Ban, log into your VPS from a different internet connection (or a phone hotspot) and run:
> `sudo fail2ban-client set sshd unbanip THEIR_IP_ADDRESS`

---

## 1. Enable UFW Firewall (Block Everything Except Web & SSH)
Right now, your backend port (8081) is accessible directly to the internet. We want to force everyone to go through Nginx. We will configure Ubuntu's default firewall to only allow HTTP, HTTPS, and SSH.

**Commands to run in VPS SSH Terminal:**
```bash
# 1. Allow SSH so you don't lock yourself out!
sudo ufw allow ssh

# 2. Allow Nginx (HTTP & HTTPS)
sudo ufw allow 'Nginx Full'

# 3. Turn on the firewall
sudo ufw enable
```
*(Press `y` and `Enter` when it warns you that it might disrupt existing SSH connections).*

---

## 2. Install Fail2Ban (Stop Brute-Force Password Guessing)
Fail2Ban watches your server logs. If someone tries to guess your SSH password 5 times incorrectly, it bans their IP.

**Commands to run in VPS SSH Terminal:**
```bash
# 1. Install Fail2Ban
sudo apt install fail2ban -y

# 2. Enable it to run automatically
sudo systemctl enable fail2ban
sudo systemctl start fail2ban
```
*Note: It automatically protects SSH right out of the box!*

---

## 3. Enable SSL / HTTPS (Let's Encrypt)
> [!WARNING]
> You **MUST** complete your Hostinger DNS setup first. Your domain (`govlyx.com` and `api.govlyx.com`) must successfully point to your VPS IP address before you run these commands, otherwise the SSL verification will fail!

**Commands to run in VPS SSH Terminal:**
```bash
# 1. Install Certbot (The SSL manager)
sudo apt install certbot python3-certbot-nginx -y

# 2. Generate and install the certificates automatically
sudo certbot --nginx -d govlyx.com -d www.govlyx.com -d api.govlyx.com
```
*(Certbot will ask for your email address and if you agree to their terms. It will automatically update your Nginx file to use HTTPS!).*

---

## 4. Nginx Rate Limiting (Stop API Spam)
To prevent a single person or bot from sending 1,000 requests per second and crashing your Spring Boot backend, we will tell Nginx to throttle them.

**Commands to run in VPS SSH Terminal:**
```bash
sudo nano /etc/nginx/nginx.conf
```
Find the `http {` section, and right below it, paste this line:
`limit_req_zone $binary_remote_addr zone=mylimit:10m rate=10r/s;`

Then, open your site config:
```bash
sudo nano /etc/nginx/sites-available/govlyx
```
Under the `location /` block for `api.govlyx.com`, add this line:
`limit_req zone=mylimit burst=20 nodelay;`

Restart Nginx:
```bash
sudo systemctl restart nginx
```
