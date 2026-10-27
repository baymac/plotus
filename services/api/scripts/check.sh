#!/bin/sh
set -eu
cd "$(dirname "$0")/../../.."
exec python3 - "$@" <<'PY'
import os,pathlib,sys
settings={}
for line in pathlib.Path('docker-local/.env').read_text().splitlines():
    if line and not line.startswith('#') and '=' in line:
        name,value=line.split('=',1);settings[name]=value
password=settings.get('API_DB_PASSWORD','')
if not password:raise SystemExit('Run docker-local/init.sh and start projects-db first.')
environment=dict(os.environ)
environment['PLOTUS_TEST_DATABASE_URL']='jdbc:postgresql://127.0.0.1:5440/plotus'
environment['PLOTUS_TEST_DATABASE_PASSWORD']=password
tasks=sys.argv[1:] or [':shared:jvmTest',':server:test',':composeApp:compileKotlinJvm',':composeApp:wasmJsBrowserDistribution']
args=['./gradlew',*tasks,'--no-daemon','-Pkotlin.compiler.execution.strategy=in-process','-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m','--console','plain']
os.execvpe(args[0],args,environment)
PY
