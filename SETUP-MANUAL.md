# Manual Setup Guide - Homedir IDP Deployment

## ⚠️ Important
Los pasos SSH al VPS están bloqueados desde este entorno. Sigue estos pasos manualmente.

## 1. Generar SSH Key en VPS

```bash
# SSH al VPS
ssh root@72.60.141.165

# Generar key para GitHub Actions
ssh-keygen -t ed25519 -f /root/.ssh/github-actions-idp -N '' -C 'github-actions-idp'

# Agregar public key a authorized_keys
cat /root/.ssh/github-actions-idp.pub >> /root/.ssh/authorized_keys

# Mostrar private key (copiar para GitHub secret)
cat /root/.ssh/github-actions-idp

# Guardar el output para usarlo en GitHub secrets
```

## 2. Configurar GitHub Secrets

### Opción A: Via Web UI

1. Ir a https://github.com/os-santiago/homedir-idp/settings/secrets/actions

2. Agregar secret: `DEPLOY_SSH_PRIVATE_KEY`
   - Click "New repository secret"
   - Name: `DEPLOY_SSH_PRIVATE_KEY`
   - Value: Pegar el contenido de `/root/.ssh/github-actions-idp` (incluir BEGIN/END)
   - Click "Add secret"

3. Agregar secret: `DEPLOY_SSH_KNOWN_HOSTS` (opcional)
   ```bash
   # Ejecutar localmente para obtener el valor:
   ssh-keyscan 72.60.141.165
   ```
   - Click "New repository secret"
   - Name: `DEPLOY_SSH_KNOWN_HOSTS`
   - Value: Pegar el output de ssh-keyscan
   - Click "Add secret"

### Opción B: Via GitHub CLI

```bash
# Copiar private key del VPS a archivo temporal
scp root@72.60.141.165:/root/.ssh/github-actions-idp /tmp/github-actions-idp

# Configurar secret con gh CLI
gh secret set DEPLOY_SSH_PRIVATE_KEY -R os-santiago/homedir-idp < /tmp/github-actions-idp

# Opcional: known hosts
ssh-keyscan 72.60.141.165 > /tmp/known_hosts
gh secret set DEPLOY_SSH_KNOWN_HOSTS -R os-santiago/homedir-idp < /tmp/known_hosts

# Limpiar archivos temporales
rm /tmp/github-actions-idp /tmp/known_hosts
```

## 3. Ejecutar Setup en VPS

```bash
# SSH al VPS
ssh root@72.60.141.165

# Opción A: Download y ejecutar script
curl -fsSL https://raw.githubusercontent.com/os-santiago/homedir-idp/master/scripts/setup-vps.sh -o /tmp/setup-idp.sh
chmod +x /tmp/setup-idp.sh
bash /tmp/setup-idp.sh

# Opción B: Ejecutar directamente
curl -fsSL https://raw.githubusercontent.com/os-santiago/homedir-idp/master/scripts/setup-vps.sh | bash
```

### Pasos del Setup Script

El script automáticamente:
1. ✅ Crea `/opt/homedir-idp/data` y subdirectorios
2. ✅ Verifica/instala Podman
3. ✅ Verifica/instala Nginx
4. ✅ Configura site Nginx en `/etc/nginx/sites-available/homedir-idp`
5. ✅ Habilita site con symlink
6. ✅ Recarga Nginx
7. ⚠️ Solicita configurar SSL (requiere DNS configurado)
8. ✅ Crea servicio systemd (opcional)
9. ✅ Configura log rotation

### Durante la ejecución

**Cuando pregunte por SSL:**
```
DNS configured? (y/n)
```

- Si DNS ya apunta a 72.60.141.165: **y** (configurará SSL automáticamente)
- Si DNS NO está configurado: **n** (configurar SSL manualmente después)

## 4. Configurar DNS (si no está configurado)

Agregar registro A en DNS:

```
Type: A
Name: homedir-idp
Value: 72.60.141.165
TTL: 3600
```

**Full domain:** homedir-idp.opensourcesantiago.io → 72.60.141.165

## 5. Configurar SSL Manualmente (si se saltó en setup)

```bash
# SSH al VPS
ssh root@72.60.141.165

# Verificar que DNS está configurado
nslookup homedir-idp.opensourcesantiago.io

# Obtener certificado Let's Encrypt
certbot --nginx -d homedir-idp.opensourcesantiago.io

# Certbot modificará automáticamente el Nginx config para HTTPS
```

## 6. Verificar Configuración

### Check 1: Nginx Config

```bash
# Test nginx config
nginx -t

# Ver config del site
cat /etc/nginx/sites-available/homedir-idp
```

### Check 2: Directories

```bash
# Verificar estructura de datos
ls -la /opt/homedir-idp/
# Debe mostrar: data/

ls -la /opt/homedir-idp/data/
# Debe mostrar: templates/ projects/ deployments/
```

### Check 3: Podman

```bash
# Verificar que puede pull images
podman pull ghcr.io/os-santiago/homedir-idp:latest || echo "Image not built yet (normal)"
```

### Check 4: SSL Certificate

```bash
# Listar certificados
certbot certificates | grep homedir-idp
```

## 7. Trigger Primer Deployment

### Opción A: Push to master

```bash
# Desde tu local
cd D:/git/homedir-idp
git checkout master
git pull
echo "# Trigger deployment" >> README.md
git add README.md
git commit -m "chore: trigger first deployment"
git push origin master
```

### Opción B: Manual Workflow Dispatch

1. Ir a https://github.com/os-santiago/homedir-idp/actions/workflows/release.yml
2. Click "Run workflow"
3. Branch: master
4. Click "Run workflow"

### Opción C: Deploy Manual (sin CI/CD)

```bash
# SSH al VPS
ssh root@72.60.141.165

# Pull image
podman pull ghcr.io/os-santiago/homedir-idp:latest

# Run container
podman run -d \
  --name homedir-idp \
  --restart unless-stopped \
  -p 8090:8090 \
  -v /opt/homedir-idp/data:/work/data:Z \
  -e QUARKUS_HTTP_HOST=0.0.0.0 \
  -e QUARKUS_HTTP_PORT=8090 \
  --label "io.containers.autoupdate=registry" \
  ghcr.io/os-santiago/homedir-idp:latest

# Esperar unos segundos
sleep 10

# Health check
curl http://localhost:8090/q/health
```

## 8. Verificar Deployment

### Internal Health Check

```bash
# Desde VPS
curl http://localhost:8090/q/health

# Expected output:
# {
#   "status": "UP",
#   "checks": []
# }
```

### External Health Check

```bash
# Desde cualquier lugar
curl https://homedir-idp.opensourcesantiago.io/q/health
```

### Ver Logs

```bash
# SSH al VPS
ssh root@72.60.141.165

# Logs del container
podman logs homedir-idp --tail 50

# Logs en tiempo real
podman logs -f homedir-idp
```

### Ver Container Status

```bash
podman ps | grep homedir-idp

# Expected output similar a:
# CONTAINER ID  IMAGE                                    COMMAND  CREATED  STATUS  PORTS                   NAMES
# abc123def456  ghcr.io/os-santiago/homedir-idp:latest            ...      Up      0.0.0.0:8090->8090/tcp  homedir-idp
```

## 9. Troubleshooting

### Container no inicia

```bash
# Ver logs
podman logs homedir-idp

# Ver events
podman events --since 5m

# Verificar puerto
ss -tuln | grep 8090
```

### Nginx 502 Bad Gateway

```bash
# Verificar container está corriendo
podman ps | grep homedir-idp

# Verificar puerto está escuchando
curl http://localhost:8090/q/health

# Ver logs de nginx
tail -f /var/log/nginx/error.log
```

### SSL no funciona

```bash
# Verificar certificado
certbot certificates

# Renovar manualmente
certbot renew --force-renewal

# Test SSL
openssl s_client -connect homedir-idp.opensourcesantiago.io:443
```

## 10. Post-Deployment

### Verificar en Browser

Abrir: https://homedir-idp.opensourcesantiago.io

Debería ver la página principal del IDP con tema oscuro.

### Verificar CI/CD

1. Ir a https://github.com/os-santiago/homedir-idp/actions
2. Ver workflow "Production Release & Deploy"
3. Debería estar verde ✅

### Monitoreo

```bash
# Stats del container
podman stats homedir-idp

# Espacio en disco
df -h /opt/homedir-idp/

# Memoria/CPU
htop
```

## 11. Cleanup (opcional)

Si necesitas empezar de cero:

```bash
# SSH al VPS
ssh root@72.60.141.165

# Stop y remove container
podman stop homedir-idp
podman rm homedir-idp

# Limpiar images
podman images | grep homedir-idp
podman rmi ghcr.io/os-santiago/homedir-idp:latest

# Remover datos (⚠️ CUIDADO: esto borra todo)
# rm -rf /opt/homedir-idp/data

# Desactivar site nginx
rm /etc/nginx/sites-enabled/homedir-idp
nginx -s reload
```

## 12. Checklist Final

- [ ] SSH key generada en VPS
- [ ] SSH public key agregada a authorized_keys
- [ ] GitHub secret `DEPLOY_SSH_PRIVATE_KEY` configurado
- [ ] DNS configurado (homedir-idp.opensourcesantiago.io → 72.60.141.165)
- [ ] Setup script ejecutado en VPS
- [ ] SSL certificate obtenido
- [ ] Nginx configurado y corriendo
- [ ] Primer deployment ejecutado (manual o via CI/CD)
- [ ] Health check responde: https://homedir-idp.opensourcesantiago.io/q/health
- [ ] Página principal carga: https://homedir-idp.opensourcesantiago.io

## Referencias

- Setup script: `scripts/setup-vps.sh`
- Deployment docs: `docs/deployment.md`
- Workflows: `.github/workflows/`
- VPS: 72.60.141.165
- Domain: homedir-idp.opensourcesantiago.io
