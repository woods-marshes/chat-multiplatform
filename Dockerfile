# =============================================================
# Stage 1: 缓存 Gradle 依赖（加速后续构建）
# =============================================================
FROM gradle:9.8.2-jdk25 AS build

USER root
RUN apt update && \
    apt install -y --no-install-recommends curl ca-certificates && \
    curl -fsSL https://deb.nodesource.com/setup_24.x | bash - && \
    apt install -y --no-install-recommends nodejs && \
    rm -rf /var/lib/apt/lists/*
USER gradle

WORKDIR /home/gradle/src

COPY --chown=gradle:gradle gradlew /home/gradle/src/
COPY --chown=gradle:gradle gradle/ /home/gradle/src/gradle/
COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts gradle.properties* /home/gradle/src/
COPY --chown=gradle:gradle build-logic/ /home/gradle/src/build-logic/

COPY --chown=gradle:gradle tiptap-bridge/package*.json /home/gradle/src/tiptap-bridge/
RUN --mount=type=cache,target=/home/gradle/.npm,uid=1000,gid=1000 \
    cd /home/gradle/src/tiptap-bridge && npm ci --legacy-peer-deps

COPY --chown=gradle:gradle . .

# uid=1000/gid=1000 确保 gradle 用户对挂载的缓存目录有读写权限
RUN --mount=type=cache,target=/home/gradle/.gradle,uid=1000,gid=1000 \
    ./gradlew :server:buildFatJar -PenableAndroid=false --no-daemon

FROM eclipse-temurin:25-jre-alpine AS runtime

RUN apk add --no-cache ffmpeg bash

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

RUN mkdir -p /app/uploads && chown -R appuser:appgroup /app

COPY --chown=appuser:appgroup --from=build /home/gradle/src/server/build/libs/fat.jar /app/app.jar

USER appuser

EXPOSE 9051

ENV JDK_JAVA_OPTIONS="-Xmx512m -Xms256m"

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- http://localhost:9051/ || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JDK_JAVA_OPTIONS -jar /app/app.jar"]

