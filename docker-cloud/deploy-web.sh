#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
./gradlew :composeApp:wasmJsBrowserDistribution --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m'
npm --prefix apps/marketing run build
node docker-cloud/scripts/build-web.mjs
cd docker-cloud
npx wrangler pages deploy dist --project-name plotus-landing --branch main
