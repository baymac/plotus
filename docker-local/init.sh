#!/bin/sh
set -eu
cd "$(dirname "$0")"
umask 077
../services/auth/scripts/init-local.sh
if ! awk -F= '$1=="API_DB_PASSWORD" && length($2)>0 {found=1} END {exit !found}' .env; then
  projects_password=$(openssl rand -hex 32)
  awk -v secret="$projects_password" '
    /^API_DB_PASSWORD=/ {print "API_DB_PASSWORD=" secret; found=1; next}
    {print}
    END {if (!found) print "API_DB_PASSWORD=" secret}
  ' .env > .env.next
  mv .env.next .env
fi
printf '%s\n' 'Local Plotus configuration is ready. Run: cd docker-local && docker compose up --build -d'
