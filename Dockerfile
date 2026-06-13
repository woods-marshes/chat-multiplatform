# =============================================================
# Stage 1: 缓存 Gradle 依赖（加速后续构建）
# =============================================================
FROM gradle:9.5.1-jdk25 AS cache

WORKDIR /home/gradle/src

COPY --chown=gradle:gradle gradlew /home/gradle/src/
COPY --chown=gradle:gradle gradle/ /home/gradle/src/gradle/
COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts gradle.properties* /home/gradle/src/
COPY --chown=gradle:gradle build-logic/ /home/gradle/src/build-logic/

COPY --chown=gradle:gradle . .

# uid=1000/gid=1000 确保 gradle 用户对挂载的缓存目录有读写权限
RUN --mount=type=cache,target=/home/gradle/.gradle,uid=1000,gid=1000 \
    ./gradlew :server:buildFatJar --no-daemon -PexcludeWeb=true

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

# FROM gradle:9.5.1-jdk25 AS cache
# ENV GRADLE_USER_HOME=/home/gradle/.gradle
#
# # 复制构建脚本和版本目录（不复制源码，充分利用 Docker 缓存层）
# COPY gradlew /home/gradle/src/
# COPY gradle/ /home/gradle/src/gradle/
# COPY settings.gradle.kts settings.gradle.kts /home/gradle/src/
# COPY build.gradle.kts gradle.properties* /home/gradle/src/
# COPY build-logic/ /home/gradle/src/build-logic/
#
# # 复制所有子模块的 build.gradle.kts（让 Gradle 解析完整依赖图）
# COPY server/build.gradle.kts /home/gradle/src/server/
# COPY web/build.gradle.kts /home/gradle/src/web/
# COPY composeApp/build.gradle.kts /home/gradle/src/composeApp/
# COPY androidApp/build.gradle.kts /home/gradle/src/androidApp/
# COPY core/model/build.gradle.kts /home/gradle/src/core/model/
# COPY core/network/build.gradle.kts /home/gradle/src/core/network/
# COPY core/common/build.gradle.kts /home/gradle/src/core/common/
# COPY core/data/build.gradle.kts /home/gradle/src/core/data/
# COPY core/domain/build.gradle.kts /home/gradle/src/core/domain/
# COPY core/database/build.gradle.kts /home/gradle/src/core/database/
# COPY core/datastore/build.gradle.kts /home/gradle/src/core/datastore/
# COPY core/database-room/build.gradle.kts /home/gradle/src/core/database-room/
# COPY core/ui/build.gradle.kts /home/gradle/src/core/ui/
# COPY core/navigation/build.gradle.kts /home/gradle/src/core/navigation/
# COPY features/auth/build.gradle.kts /home/gradle/src/features/auth/
# COPY features/chat/build.gradle.kts /home/gradle/src/features/chat/
# COPY features/contacts/build.gradle.kts /home/gradle/src/features/contacts/
# COPY features/conversations/build.gradle.kts /home/gradle/src/features/conversations/
# COPY features/profile/build.gradle.kts /home/gradle/src/features/profile/
# COPY features/search/build.gradle.kts /home/gradle/src/features/search/
# COPY features/settings/build.gradle.kts /home/gradle/src/features/settings/
#
# # 预下载依赖
# WORKDIR /home/gradle/src
# RUN chmod +x gradlew
# RUN ./gradlew :server:dependencies --no-daemon -PexcludeWeb=true
#
# # =============================================================
# # Stage 2: 构建 Fat JAR
# # =============================================================
# FROM gradle:9.5.1-jdk25 AS build
#
# # 复用 Stage 1 的 Gradle 缓存
# COPY --from=cache /home/gradle/.gradle /home/gradle/.gradle
#
# # 复制完整项目源码
# COPY --chown=gradle:gradle . /home/gradle/src
# WORKDIR /home/gradle/src
#
# # 构建 server 模块的 fat JAR
# RUN ./gradlew :server:buildFatJar --no-daemon -PexcludeWeb=true
#
# # =============================================================
# # Stage 3: 精简运行时镜像
# # =============================================================
# FROM eclipse-temurin:25-jre-alpine AS runtime
#
# # 安装 jave2 所需的原生 ffmpeg
# RUN apk add --no-cache ffmpeg bash
#
# EXPOSE 9051
#
# # 创建应用目录
# RUN mkdir /app
#
# # 只复制 server 的 fat JAR
# COPY --from=build /home/gradle/src/server/build/libs/*.jar /app/app.jar
#
# # JVM 运行时参数（JDK_JAVA_OPTIONS 是 Java 9+ 的标准环境变量，JVM 自动读取）
# ENV JDK_JAVA_OPTIONS="-Xmx512m -Xms256m"
#
# # 健康检查（每 30s 检查一次，超时 3s，最多重试 3 次）
# HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
#     CMD wget -qO- http://localhost:9051/ || exit 1
#
# ENTRYPOINT ["sh", "-c", "exec java $JDK_JAVA_OPTIONS -jar /app/app.jar"]
