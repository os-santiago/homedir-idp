# Homedir IDP - Deployment Guide

## Overview

Homedir IDP is deployed to the same VPS as the main Homedir application, accessible at:

**Production URL:** https://homedir-idp.opensourcesantiago.io

## Architecture

```
Internet
   ↓
Nginx Reverse Proxy (VPS)
   ├─→ homedir.opensourcesantiago.io → Podman container (port 8080)
   └─→ homedir-idp.opensourcesantiago.io → Podman container (port 8090)
```

## VPS Configuration

### Container Setup

**Container Name:** `homedir-idp`  
**Port:** `8090` (internal) → `8090` (host)  
**Image Registry:** `ghcr.io/os-santiago/homedir-idp`  
**Data Volume:** `/opt/homedir-idp/data` → `/work/data` (inside container)

### Nginx Configuration

Location: `/etc/nginx/sites-available/homedir-idp`

```nginx
server {
    listen 80;
    listen [::]:80;
    server_name homedir-idp.opensourcesantiago.io;

    # Redirect HTTP to HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name homedir-idp.opensourcesantiago.io;

    # SSL Configuration (Let's Encrypt)
    ssl_certificate /etc/letsencrypt/live/homedir-idp.opensourcesantiago.io/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/homedir-idp.opensourcesantiago.io/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    # Security Headers
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;

    # Proxy to Podman container
    location / {
        proxy_pass http://localhost:8090;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_buffering off;
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # Health check endpoint (no auth required)
    location /q/health {
        proxy_pass http://localhost:8090/q/health;
        access_log off;
    }
}
```

Enable site:
```bash
ln -s /etc/nginx/sites-available/homedir-idp /etc/nginx/sites-enabled/
nginx -t
systemctl reload nginx
```

### SSL Certificate

```bash
# Install certbot
apt-get install certbot python3-certbot-nginx

# Obtain certificate
certbot --nginx -d homedir-idp.opensourcesantiago.io

# Auto-renewal is configured via cron
```

## CI/CD Pipeline

### Workflow: PR Validation (`.github/workflows/pr-check.yml`)

**Triggers:**
- Pull request to `master`/`main`
- Manual dispatch

**Steps:**
1. Build Quarkus app
2. Run tests
3. Code quality checks

**Gates:**
- All tests must pass
- Build must succeed

### Workflow: Production Release (`.github/workflows/release.yml`)

**Triggers:**
- Push to `master` branch
- Manual dispatch

**Steps:**

1. **Build**
   - Calculate version: `0.1.0-{commit-sha}`
   - Build with Maven
   - Create JAR artifact

2. **Container Image**
   - Build Docker image (UBI8 + OpenJDK 21)
   - Tag: `ghcr.io/os-santiago/homedir-idp:{version}` + `latest`
   - Push to GitHub Container Registry

3. **Deploy to VPS**
   - SSH into VPS (72.60.141.165)
   - Pull new image
   - Stop existing container
   - Start new container with:
     - Port mapping: `8090:8090`
     - Volume: `/opt/homedir-idp/data:/work/data`
     - Auto-restart policy
   - Internal health check (`/q/health`)

4. **External Verification**
   - Wait for service to be accessible
   - Curl `https://homedir-idp.opensourcesantiago.io/q/health`
   - Retry up to 30 times (5 minutes)

5. **GitHub Release**
   - Create release tag
   - Attach build metadata
   - Link to deployed URL

**Environment Variables:**
- `DEPLOY_SSH_HOST` = `72.60.141.165` (or from vars)
- `DEPLOY_SSH_USER` = `root` (or from vars)
- `DEPLOY_SSH_PORT` = `22` (or from vars)
- `DEPLOY_SSH_PRIVATE_KEY` = SSH key (from secrets)
- `IDP_HEALTHCHECK_URL` = `https://homedir-idp.opensourcesantiago.io/q/health`

## Manual Deployment

### SSH into VPS

```bash
ssh root@72.60.141.165
```

### Pull and Deploy

```bash
# Pull latest image
podman pull ghcr.io/os-santiago/homedir-idp:latest

# Stop existing container
podman stop homedir-idp
podman rm homedir-idp

# Run new container
podman run -d \
  --name homedir-idp \
  --restart unless-stopped \
  -p 8090:8090 \
  -v /opt/homedir-idp/data:/work/data:Z \
  -e QUARKUS_HTTP_HOST=0.0.0.0 \
  -e QUARKUS_HTTP_PORT=8090 \
  --label "io.containers.autoupdate=registry" \
  ghcr.io/os-santiago/homedir-idp:latest

# Check logs
podman logs -f homedir-idp

# Health check
curl http://localhost:8090/q/health
```

### Check Status

```bash
# Container status
podman ps | grep homedir-idp

# Logs (last 100 lines)
podman logs homedir-idp --tail 100

# Follow logs
podman logs -f homedir-idp

# Resource usage
podman stats homedir-idp
```

## Rollback

```bash
# List available images
podman images | grep homedir-idp

# Deploy specific version
podman stop homedir-idp
podman rm homedir-idp
podman run -d \
  --name homedir-idp \
  --restart unless-stopped \
  -p 8090:8090 \
  -v /opt/homedir-idp/data:/work/data:Z \
  ghcr.io/os-santiago/homedir-idp:0.1.0-abc1234

# Verify
curl https://homedir-idp.opensourcesantiago.io/q/health
```

## Monitoring

### Health Checks

**Endpoint:** `/q/health`

**Response (healthy):**
```json
{
  "status": "UP",
  "checks": []
}
```

### Logs

```bash
# Application logs
podman logs homedir-idp

# Nginx access logs
tail -f /var/log/nginx/access.log | grep homedir-idp

# Nginx error logs
tail -f /var/log/nginx/error.log
```

### Metrics

**Endpoint:** `/q/metrics` (if enabled)

## Data Persistence

**Host Path:** `/opt/homedir-idp/data`  
**Container Path:** `/work/data`

**Contents:**
- `templates/` - Template definitions (JSON files)
- `projects/` - Project metadata
- `deployments/` - Deployment history

**Backup:**
```bash
# Backup data directory
tar -czf homedir-idp-data-$(date +%Y%m%d).tar.gz /opt/homedir-idp/data

# Restore from backup
tar -xzf homedir-idp-data-20260801.tar.gz -C /
```

## Troubleshooting

### Container won't start

```bash
# Check logs
podman logs homedir-idp

# Check image
podman images | grep homedir-idp

# Check port conflicts
ss -tuln | grep 8090
```

### Health check fails

```bash
# Check if container is running
podman ps | grep homedir-idp

# Check application logs
podman logs homedir-idp --tail 50

# Check inside container
podman exec -it homedir-idp curl http://localhost:8090/q/health
```

### Nginx errors

```bash
# Test nginx config
nginx -t

# Check nginx logs
tail -f /var/log/nginx/error.log

# Reload nginx
systemctl reload nginx
```

### SSL certificate issues

```bash
# Check certificate expiry
certbot certificates

# Renew manually
certbot renew --force-renewal

# Check nginx SSL config
openssl s_client -connect homedir-idp.opensourcesantiago.io:443
```

## GitHub Secrets Configuration

Required secrets in repository settings:

1. **`DEPLOY_SSH_PRIVATE_KEY`**
   - SSH private key for VPS access
   - Generate: `ssh-keygen -t ed25519 -C "github-actions-idp"`
   - Add public key to VPS: `/root/.ssh/authorized_keys`

2. **`DEPLOY_SSH_KNOWN_HOSTS`** (optional)
   - Known hosts fingerprint
   - Generate: `ssh-keyscan 72.60.141.165`

## Repository Variables Configuration

Optional variables in repository settings:

- `DEPLOY_SSH_HOST` = `72.60.141.165` (default if not set)
- `DEPLOY_SSH_USER` = `root` (default if not set)
- `DEPLOY_SSH_PORT` = `22` (default if not set)
- `IDP_HEALTHCHECK_URL` = `https://homedir-idp.opensourcesantiago.io/q/health`

## Post-Deployment Verification

After deployment completes:

1. **Check GitHub Actions:** Workflow should be green
2. **Check URL:** Visit https://homedir-idp.opensourcesantiago.io
3. **Check health:** `curl https://homedir-idp.opensourcesantiago.io/q/health`
4. **Check logs:** `ssh root@72.60.141.165 "podman logs homedir-idp --tail 20"`
5. **Check version:** Look for version in logs or UI footer

## References

- Main Homedir deployment: Similar setup on port 8080
- VPS Host: 72.60.141.165
- Container registry: GitHub Container Registry (ghcr.io)
- Reverse proxy: Nginx
- Container runtime: Podman
