FROM gradle:8.14.3-jdk17 AS build
USER root
WORKDIR /workspace
ENV GRADLE_USER_HOME=/root/.gradle
COPY . .
RUN --mount=type=cache,id=plotus-gradle,target=/root/.gradle,sharing=locked \
    gradle :server:installDist --no-daemon --console plain -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m"
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/services/api/build/install/server/ /app/
ENV HOST=0.0.0.0 PORT=4000
USER 10001:10001
EXPOSE 4000
CMD ["/app/bin/server"]
