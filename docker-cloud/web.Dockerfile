FROM gradle:8.14.3-jdk17 AS build
USER root
RUN apt-get update && apt-get install -y --no-install-recommends libatomic1 && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
ENV GRADLE_USER_HOME=/root/.gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY kotlin-js-store ./kotlin-js-store
COPY apps/client ./apps/client
COPY packages/shared ./packages/shared
COPY services/api/build.gradle.kts ./services/api/build.gradle.kts
RUN --mount=type=cache,id=plotus-gradle,target=/root/.gradle,sharing=locked \
    gradle :composeApp:wasmJsBrowserDistribution --no-daemon --console plain -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m"
FROM caddy:2.10-alpine
COPY docker-cloud/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /workspace/apps/client/build/dist/wasmJs/productionExecutable/ /srv/
EXPOSE 80 443
