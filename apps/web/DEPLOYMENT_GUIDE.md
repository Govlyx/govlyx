# Govlyx Frontend Deployment Guide

This document outlines the step-by-step process for editing, compiling, and deploying the Govlyx frontend to the Hostinger VPS.

## Prerequisites
- **Local machine:** Node.js (v18+) and npm installed.
- **SSH Client:** PuTTY (Windows) or terminal (Mac/Linux).
- **VPS IP Address:** `187.127.141.153`
- **Server OS:** Ubuntu 24.04 LTS

---

## Workflow Overview
```
[1. Edit Files] ---> [2. Build locally (npm run build)] ---> [3. Upload to VPS (scp)] ---> [4. Fix Permissions (PuTTY)]
```

---

## Step 1: Making Code Changes
All code edits are done inside the `Govlyx` project folder.

### 1. Development Configuration
* Local development files use a `.env` file to target the backend.
* For local dev to talk to the live VPS backend, ensure `.env` has:
  ```properties
  VITE_API_URL=https://api.govlyx.com
  ```
* Start the local server:
  ```bash
  npm run dev
  ```

### 2. Production URL Configuration
The production backend URL is configured to fall back to `https://api.govlyx.com` in two files:
* `src/api/axiosConfig.ts` (Handles API calls and WebSockets via Axios)
* `src/utils/apiUrl.ts` (Handles general fetch requests)

---

## Step 2: Build the Frontend
Once your UI changes are completed and tested locally, compile the codebase for production:

1. Open your terminal in the `Govlyx` directory.
2. Run:
   ```bash
   npm run build
   ```
This compiles and optimizes all React, TypeScript, and TailwindCSS code, outputting static files into a folder named **`dist`**.

---

## Step 3: Upload the files to the VPS
Use the secure copy (`scp`) command in your local terminal to upload the compiled `dist` folder to your VPS web root:

```bash
scp -r dist/* root@187.127.141.153:/var/www/govlyx-frontend/
```
*When prompted, enter your VPS root password.*

---

## Step 4: Reset Permissions on the Server (PuTTY)
Because files uploaded via `scp` are owned by `root`, Nginx (running as the restricted user `www-data`) will be blocked from reading them. 

Log in to the VPS via **PuTTY** and run these commands to reset ownership and permissions:

```bash
# 1. Transfer folder ownership to Nginx user
sudo chown -R www-data:www-data /var/www/govlyx-frontend

# 2. Set safe permissions (owner full access, public read-only)
sudo chmod -R 755 /var/www/govlyx-frontend
```
*If you skip this step, visitors will see a blank page or a **403 Forbidden** error.*

---

## Staging / Testing Environment (Vercel)
Vercel is kept active as a **Staging/Testing environment**:
- Pushing code to GitHub will automatically trigger a deployment to Vercel (e.g. `govlyx-io.vercel.app`).
- The Vercel project environment variable `VITE_API_URL` points to `https://api.govlyx.com`, letting you test staging builds against the live database.
- Once a feature is verified on Vercel, deploy to production using **Step 2** and **Step 3** above.
