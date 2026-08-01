#!/bin/bash
# Homedir IDP - VPS Setup Script
# Run this script on the VPS to prepare for deployments

set -euo pipefail

echo "🚀 Setting up VPS for Homedir IDP deployment..."

# Variables
IDP_DATA_DIR="/opt/homedir-idp/data"
NGINX_SITE="homedir-idp"
DOMAIN="homedir-idp.opensourcesantiago.io"
CONTAINER_PORT="8090"

# Check if running as root
if [ "$EUID" -ne 0 ]; then
  echo "❌ Please run as root"
  exit 1
fi

# 1. Create data directory
echo "📁 Creating data directories..."
mkdir -p "${IDP_DATA_DIR}"/{templates,projects,deployments}
chmod 755 "${IDP_DATA_DIR}"
echo "✅ Data directory created: ${IDP_DATA_DIR}"

# 2. Check if Podman is installed
if ! command -v podman &> /dev/null; then
  echo "⚠️  Podman not found. Installing..."
  apt-get update
  apt-get install -y podman
  echo "✅ Podman installed"
else
  echo "✅ Podman already installed"
fi

# 3. Check if Nginx is installed
if ! command -v nginx &> /dev/null; then
  echo "⚠️  Nginx not found. Installing..."
  apt-get update
  apt-get install -y nginx
  systemctl enable nginx
  systemctl start nginx
  echo "✅ Nginx installed"
else
  echo "✅ Nginx already installed"
fi

# 4. Configure Nginx site
echo "🌐 Configuring Nginx..."
cat > "/etc/nginx/sites-available/${NGINX_SITE}" <<EOF
server {
    listen 80;
    listen [::]:80;
    server_name ${DOMAIN};

    # Redirect HTTP to HTTPS (will be enabled after SSL cert)
    # return 301 https://\$server_name\$request_uri;

    # Temporary: proxy to container (before SSL)
    location / {
        proxy_pass http://localhost:${CONTAINER_PORT};
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    location /q/health {
        proxy_pass http://localhost:${CONTAINER_PORT}/q/health;
        access_log off;
    }
}
EOF

# Enable site
if [ ! -L "/etc/nginx/sites-enabled/${NGINX_SITE}" ]; then
  ln -s "/etc/nginx/sites-available/${NGINX_SITE}" "/etc/nginx/sites-enabled/${NGINX_SITE}"
  echo "✅ Nginx site enabled"
fi

# Test nginx config
nginx -t
systemctl reload nginx
echo "✅ Nginx configured and reloaded"

# 5. Setup SSL with Let's Encrypt
echo "🔒 Setting up SSL..."
if ! command -v certbot &> /dev/null; then
  echo "Installing certbot..."
  apt-get install -y certbot python3-certbot-nginx
fi

# Obtain certificate (requires DNS to be configured)
if certbot certificates | grep -q "${DOMAIN}"; then
  echo "✅ SSL certificate already exists for ${DOMAIN}"
else
  echo "📜 Obtaining SSL certificate..."
  echo "⚠️  Make sure DNS for ${DOMAIN} points to this server!"
  read -p "DNS configured? (y/n) " -n 1 -r
  echo
  if [[ $REPLY =~ ^[Yy]$ ]]; then
    certbot --nginx -d "${DOMAIN}" --non-interactive --agree-tos --email admin@opensourcesantiago.io || {
      echo "⚠️  SSL setup failed. You can run certbot manually later:"
      echo "   certbot --nginx -d ${DOMAIN}"
    }
  else
    echo "⏭️  Skipping SSL setup. Run this when DNS is ready:"
    echo "   certbot --nginx -d ${DOMAIN}"
  fi
fi

# 6. Pull container image
echo "📥 Pulling container image..."
if podman pull ghcr.io/os-santiago/homedir-idp:latest; then
  echo "✅ Image pulled successfully"
else
  echo "⚠️  Image pull failed. This is normal if the image hasn't been built yet."
  echo "   The CI/CD pipeline will handle deployment after the first release."
fi

# 7. Setup systemd service for auto-restart (optional)
echo "🔄 Creating systemd service..."
mkdir -p /etc/systemd/system

cat > /etc/systemd/system/homedir-idp.service <<'EOF'
[Unit]
Description=Homedir IDP Container
After=network-online.target
Wants=network-online.target

[Service]
Type=forking
Restart=always
RestartSec=10
TimeoutStartSec=120
ExecStartPre=-/usr/bin/podman stop homedir-idp
ExecStartPre=-/usr/bin/podman rm homedir-idp
ExecStart=/usr/bin/podman run -d \
  --name homedir-idp \
  --restart unless-stopped \
  -p 8090:8090 \
  -v /opt/homedir-idp/data:/work/data:Z \
  -e QUARKUS_HTTP_HOST=0.0.0.0 \
  -e QUARKUS_HTTP_PORT=8090 \
  --label "io.containers.autoupdate=registry" \
  ghcr.io/os-santiago/homedir-idp:latest
ExecStop=/usr/bin/podman stop homedir-idp
ExecStopPost=/usr/bin/podman rm -f homedir-idp

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
echo "✅ Systemd service created (not enabled by default)"
echo "   To enable auto-start: systemctl enable homedir-idp"

# 8. Firewall configuration (if ufw is installed)
if command -v ufw &> /dev/null; then
  echo "🔥 Configuring firewall..."
  ufw allow 80/tcp comment 'HTTP'
  ufw allow 443/tcp comment 'HTTPS'
  ufw allow 22/tcp comment 'SSH'
  echo "✅ Firewall rules added"
fi

# 9. Setup log rotation
echo "📜 Configuring log rotation..."
cat > /etc/logrotate.d/homedir-idp <<'EOF'
/var/log/homedir-idp/*.log {
    daily
    rotate 7
    compress
    delaycompress
    missingok
    notifempty
    create 0644 root root
}
EOF
mkdir -p /var/log/homedir-idp
echo "✅ Log rotation configured"

# Summary
echo ""
echo "✅ VPS setup complete!"
echo ""
echo "📝 Summary:"
echo "  - Data directory: ${IDP_DATA_DIR}"
echo "  - Nginx config: /etc/nginx/sites-available/${NGINX_SITE}"
echo "  - Domain: ${DOMAIN}"
echo "  - Container port: ${CONTAINER_PORT}"
echo "  - Systemd service: /etc/systemd/system/homedir-idp.service"
echo ""
echo "📋 Next steps:"
echo "  1. Configure DNS: ${DOMAIN} → $(curl -s ifconfig.me)"
echo "  2. Run SSL: certbot --nginx -d ${DOMAIN}"
echo "  3. Push code to trigger CI/CD deployment"
echo "  4. Verify: https://${DOMAIN}/q/health"
echo ""
echo "🔧 Manual deployment:"
echo "  podman pull ghcr.io/os-santiago/homedir-idp:latest"
echo "  podman stop homedir-idp && podman rm homedir-idp"
echo "  podman run -d --name homedir-idp -p ${CONTAINER_PORT}:${CONTAINER_PORT} \\"
echo "    -v ${IDP_DATA_DIR}:/work/data:Z \\"
echo "    ghcr.io/os-santiago/homedir-idp:latest"
echo ""
