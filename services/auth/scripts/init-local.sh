#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
umask 077
mkdir -p .local
chmod 700 .local
if [ ! -f .local/signing-key.pem ]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out .local/signing-key.pem 2>/dev/null
fi
if [ ! -f .env ]; then
  cp .env.example .env
  auth_db_password=$(openssl rand -hex 32)
  awk -v secret="$auth_db_password" -v uid="$(id -u)" -v gid="$(id -g)" '
    /^AUTH_DB_PASSWORD=/{print "AUTH_DB_PASSWORD=" secret;next}
    /^PLOTUS_AUTH_UID=/{print "PLOTUS_AUTH_UID=" uid;next}
    /^PLOTUS_AUTH_GID=/{print "PLOTUS_AUTH_GID=" gid;next}
    {print}
  ' .env > .local/env-next
  mv .local/env-next .env
fi
printf '%s\n' 'Local auth configuration is ready. Run: docker compose up --build -d'
