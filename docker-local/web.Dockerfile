FROM node:22-bookworm-slim AS viewer-node
FROM gradle:8.14.3-jdk17 AS build
USER root
COPY --from=viewer-node /usr/local/bin/node /usr/local/bin/node
COPY --from=viewer-node /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/npm
RUN apt-get update && apt-get install -y --no-install-recommends libatomic1 && rm -rf /var/lib/apt/lists/*
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm
WORKDIR /workspace
ENV GRADLE_USER_HOME=/root/.gradle
COPY . .
RUN --mount=type=cache,id=plotus-gradle,target=/root/.gradle,sharing=locked \
    gradle :composeApp:wasmJsBrowserDevelopmentWebpack --no-daemon --console plain -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m"
RUN mkdir -p /web && cp -R apps/client/build/processedResources/wasmJs/main/. /web/ && \
    cp -R apps/client/build/kotlin-webpack/wasmJs/developmentExecutable/. /web/
FROM node:22-alpine AS site
WORKDIR /workspace
COPY apps/marketing ./apps/marketing
COPY docker-cloud/scripts/build-web.mjs ./docker-cloud/scripts/build-web.mjs
COPY docker-cloud/edge-worker.mjs ./docker-cloud/edge-worker.mjs
COPY --from=build /web /app
RUN npm --prefix apps/marketing run build && node docker-cloud/scripts/build-web.mjs --app-dist /app && \
    rm docker-cloud/dist/_worker.js docker-cloud/dist/_routes.json docker-cloud/dist/_headers docker-cloud/dist/_redirects
FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY docker-local/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=site /workspace/docker-cloud/dist/ /usr/share/nginx/html/
EXPOSE 8080
