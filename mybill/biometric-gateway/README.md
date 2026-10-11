# Minha Enterprises — Local Biometric Gateway for Hikvision DS-K1T8005EFWX-B

## Overview

The **Local Biometric Gateway** provides automated, resilient synchronization between the local **Hikvision DS-K1T8005EFWX-B** biometric reader and your cloud-hosted application (`minhaenterprises.in` / `103.235.106.107`).

---

## Architecture

```text
[Hikvision DS-K1T8005EFWX-B]
(LAN IP: 10.255.19.242:443)
           │
           │ ISAPI Digest Auth (LAN HTTPS)
           ▼
[Local Biometric Gateway Service]  (Running on office PC / server)
  • Reads AcsEvent punch records via ISAPI
  • Maintains durable checkpoint in checkpoint.json
  • Auto-reconnects and retries on internet outage
           │
           │ Outbound HTTPS/HTTP Ingestion
           ▼
[Cloud Application]
  1. https://minhaenterprises.in/api/biometric-devices/punches
  2. http://103.235.106.107:1104/api/biometric-devices/punches
           │
           ▼
[Attendance Service & Calculation Engine]
  • Deduplicates punches within 1-minute window
  • Calculates daily attendance (IN/OUT, Late, Overtime, Half-day)
  • Persists to MongoDB
```

---

## Configuration (`config.json`)

```json
{
  "device": {
    "ip": "10.255.19.242",
    "port": 443,
    "useHttps": true,
    "username": "admin",
    "password": "YOUR_DEVICE_PASSWORD",
    "deviceId": "DEV-2C59AE5A"
  },
  "cloud": {
    "endpoints": [
      "https://minhaenterprises.in/api/biometric-devices/punches",
      "http://103.235.106.107:1104/api/biometric-devices/punches"
    ],
    "timeoutMs": 10000
  },
  "sync": {
    "intervalSeconds": 10,
    "batchSize": 30,
    "checkpointFile": "./checkpoint.json"
  }
}
```

---

## Running the Gateway

### Option 1: Double-click Startup Batch File
Double-click `start-gateway.bat` in this folder. It will keep running in the background and automatically restart if any network glitch occurs.

### Option 2: Command Line
```bash
# Test device connectivity
node gateway.js --test-device

# Run single sync cycle
node gateway.js --sync-once

# Start continuous daemon
node gateway.js
```

### Option 3: Run as Windows Service (Auto-start on Boot)
Using PM2 or NSSM:
```bash
npm install -g pm2
pm2 start gateway.js --name "minha-biometric-gateway"
pm2 save
pm2 startup
```

---

## Nginx Reverse Proxy Configuration (Cloud Server)

To ensure `https://minhaenterprises.in/api/` forwards directly to Spring Boot on port 1104, ensure the following block exists in your cloud server's Nginx configuration (`/etc/nginx/sites-available/minhaenterprises.in`):

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:1104/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 60s;
}
```

Reload Nginx after saving:
```bash
sudo nginx -t && sudo system-restart nginx # or sudo systemctl reload nginx
```
