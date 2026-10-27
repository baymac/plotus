FROM gradle:8.14.3-jdk17 AS build
USER root
RUN apt-get update && apt-get install -y --no-install-recommends libatomic1 && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
ENV GRADLE_USER_HOME=/root/.gradle
COPY . .
RUN --mount=type=cache,id=plotus-gradle,target=/root/.gradle,sharing=locked \
    gradle :composeApp:wasmJsBrowserDevelopmentWebpack --no-daemon --console plain -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m"
RUN mkdir -p /web && cp -R apps/client/build/processedResources/wasmJs/main/. /web/ && \
    cp -R apps/client/build/kotlin-webpack/wasmJs/developmentExecutable/. /web/
FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY docker-local/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /web /usr/share/nginx/html
EXPOSE 8080
