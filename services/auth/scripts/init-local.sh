#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
auth_repo_dir=$(cd ../.. && pwd)
auth_env_file="$auth_repo_dir/docker-local/.env"
umask 077
mkdir -p .local
chmod 700 .local
if [ ! -f .local/signing-key.pem ]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out .local/signing-key.pem 2>/dev/null
fi
if [ ! -f "$auth_env_file" ]; then
  cp "$auth_repo_dir/docker-local/.env.example" "$auth_env_file"
  auth_db_password=$(openssl rand -hex 32)
  awk -v secret="$auth_db_password" -v uid="$(id -u)" -v gid="$(id -g)" '
    /^AUTH_DB_PASSWORD=/{print "AUTH_DB_PASSWORD=" secret;next}
    /^PLOTUS_AUTH_UID=/{print "PLOTUS_AUTH_UID=" uid;next}
    /^PLOTUS_AUTH_GID=/{print "PLOTUS_AUTH_GID=" gid;next}
    {print}
  ' "$auth_env_file" > .local/env-next
  mv .local/env-next "$auth_env_file"
fi
printf '%s\n' 'Local auth configuration is ready. Run: cd docker-local && docker compose up --build -d'
