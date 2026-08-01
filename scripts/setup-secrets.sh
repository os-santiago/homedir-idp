#!/bin/bash
# Script para configurar GitHub secrets para homedir-idp
# Ejecutar DESPUÉS de generar la SSH key en el VPS

set -euo pipefail

echo "🔐 Configurando GitHub Secrets para homedir-idp..."

# Variables
REPO="os-santiago/homedir-idp"
VPS_HOST="72.60.141.165"
VPS_USER="root"
KEY_PATH="/root/.ssh/github-actions-idp"

# Check if gh CLI is authenticated
if ! gh auth status &>/dev/null; then
  echo "❌ gh CLI no está autenticado"
  echo "Ejecuta: gh auth login"
  exit 1
fi

# Step 1: Generate SSH key on VPS (if not exists)
echo "📝 Paso 1: Generar SSH key en VPS..."
echo "Ejecuta en el VPS:"
echo "  ssh ${VPS_USER}@${VPS_HOST}"
echo "  ssh-keygen -t ed25519 -f ${KEY_PATH} -N '' -C 'github-actions-idp'"
echo "  cat ${KEY_PATH}.pub >> /root/.ssh/authorized_keys"
echo ""
read -p "¿SSH key generada en VPS? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
  echo "⏭️  Saltando configuración. Ejecuta el script cuando la key esté lista."
  exit 0
fi

# Step 2: Fetch private key from VPS
echo "📥 Paso 2: Obteniendo private key del VPS..."
TEMP_KEY="/tmp/github-actions-idp-$(date +%s)"

if scp "${VPS_USER}@${VPS_HOST}:${KEY_PATH}" "${TEMP_KEY}"; then
  echo "✅ Key descargada a ${TEMP_KEY}"
else
  echo "❌ Error descargando key del VPS"
  echo "Método alternativo:"
  echo "  1. SSH al VPS: ssh ${VPS_USER}@${VPS_HOST}"
  echo "  2. Cat la key: cat ${KEY_PATH}"
  echo "  3. Copiar y pegar en GitHub manualmente"
  echo "     https://github.com/${REPO}/settings/secrets/actions"
  exit 1
fi

# Step 3: Set DEPLOY_SSH_PRIVATE_KEY secret
echo "🔑 Paso 3: Configurando secret DEPLOY_SSH_PRIVATE_KEY..."
if gh secret set DEPLOY_SSH_PRIVATE_KEY -R "${REPO}" < "${TEMP_KEY}"; then
  echo "✅ Secret DEPLOY_SSH_PRIVATE_KEY configurado"
else
  echo "❌ Error configurando secret"
  echo "Configura manualmente:"
  echo "  gh secret set DEPLOY_SSH_PRIVATE_KEY -R ${REPO} < ${TEMP_KEY}"
fi

# Step 4: Set DEPLOY_SSH_KNOWN_HOSTS secret (optional)
echo "🔑 Paso 4: Configurando secret DEPLOY_SSH_KNOWN_HOSTS..."
TEMP_HOSTS="/tmp/known_hosts-$(date +%s)"
ssh-keyscan "${VPS_HOST}" > "${TEMP_HOSTS}" 2>/dev/null

if gh secret set DEPLOY_SSH_KNOWN_HOSTS -R "${REPO}" < "${TEMP_HOSTS}"; then
  echo "✅ Secret DEPLOY_SSH_KNOWN_HOSTS configurado"
else
  echo "⚠️  Secret DEPLOY_SSH_KNOWN_HOSTS no configurado (opcional)"
fi

# Cleanup
echo "🧹 Limpiando archivos temporales..."
rm -f "${TEMP_KEY}" "${TEMP_HOSTS}"

# Verify
echo ""
echo "✅ Verificando secrets configurados..."
gh secret list -R "${REPO}"

echo ""
echo "🎉 ¡Secrets configurados exitosamente!"
echo ""
echo "📝 Próximos pasos:"
echo "  1. Ejecutar setup en VPS: ssh ${VPS_USER}@${VPS_HOST}"
echo "     curl -fsSL https://raw.githubusercontent.com/${REPO}/master/scripts/setup-vps.sh | bash"
echo "  2. Trigger deployment: git push origin master"
echo "  3. Verificar: https://homedir-idp.opensourcesantiago.io/q/health"
