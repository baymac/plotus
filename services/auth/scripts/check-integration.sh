#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
exec python3 - <<'PY'
import os, pathlib
settings = {}
for line in pathlib.Path('../../docker-local/.env').read_text().splitlines():
    if line and not line.startswith('#') and '=' in line:
        name, value = line.split('=', 1)
        settings[name] = value
password = settings.get('AUTH_DB_PASSWORD', '')
if not password or any(c not in '0123456789abcdef' for c in password):
    raise SystemExit('Run scripts/init-local.sh; test storage requires its generated password.')
environment = dict(os.environ)
environment['PLOTUS_AUTH_TEST_DATABASE_URL'] = f'postgres://plotus_auth:{password}@127.0.0.1:5441/plotus_auth?sslmode=disable'
environment['PLOTUS_AUTH_TEST_REDIS_URL'] = 'redis://127.0.0.1:6381/15'
os.execvpe('go', ['go', 'test', '-count=1', './...'], environment)
PY
