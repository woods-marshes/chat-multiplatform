# 依赖版本清单

> 所有依赖版本以 `gradle/libs.versions.toml` 为准，本文档为参考快照。

## Kotlin & Compose

| 依赖 | 版本 |
|---|---|
| Kotlin | 2.3.21 |
| Compose Multiplatform | 1.12.0-alpha01 |
| Compose Material3 | 1.12.0-alpha01 |
| Material3 Adaptive | 1.3.0-beta01 |
| Compose Hot Reload | 1.1.1 |
| Compose Material Icons | 1.7.3 |

## AndroidX

| 依赖 | 版本 |
|---|---|
| AGP | 9.2.1 |
| compileSdk / targetSdk | 37 |
| minSdk | 24 |
| Navigation 3 | 1.1.1 |
| Navigation Event | 1.1.0 |
| Lifecycle | 2.11.0-beta01 |
| Activity | 1.13.0 |
| Core KTX | 1.19.0 |
| Window Core | 1.5.1 |
| SavedState | 1.4.0 |
| DataStore | 1.3.0-alpha09 |
| Paging | 3.5.0 |

## Server

| 依赖 | 版本 |
|---|---|
| Ktor | 3.5.0 |
| Exposed ORM | 1.3.0 |
| H2 | 2.4.240 |
| PostgreSQL JDBC | 42.7.11 |
| HikariCP | 7.0.2 |
| Logback | 1.5.34 |
| kotlin-logging | 8.0.4 |
| Commons Codec | 1.22.0 |
| BCrypt | 0.10.2 |
| Jave2 (ffmpeg) | 3.5.0 |

## Koin DI

| 依赖 | 版本 |
|---|---|
| Koin Core / Compose / Ktor | 4.2.1 |

## 数据库（客户端）

| 依赖 | 版本 |
|---|---|
| SQLDelight | 2.3.2 |
| SQL.js (Web) | 1.8.0 |
| Room 3.0 KMP | 3.0.0-alpha06 |

## 序列化 & 协程

| 依赖 | 版本 |
|---|---|
| kotlinx-serialization | 1.11.0 |
| kotlinx-coroutines | 1.11.0 |
| kotlinx-datetime | 0.8.0 |
| kotlinx-io | 0.9.0 |

## 其他

| 依赖 | 版本 |
|---|---|
| Coil 3 | 3.5.0 |
| Lyricist (i18n) | 1.8.0-compose-1.10 |
| Miuix (主题) | 0.9.2 |
| Multiplatform Settings | 1.3.0 |
| Kotlin Result | 2.3.1 |
| UUID Creator | 6.1.1 |
| Thumbnailator | 0.4.21 |
| FileKit | 0.14.1 |
| KSP | 2.3.4 |
| BuildKonfig | 0.21.2 |

## Web (Kobweb)

| 依赖 | 版本 |
|---|---|
| Kobweb | 0.24.0 |

## Gradle Daemon

- `-Xmx3072M`
- 代理: `127.0.0.1:10808`（`gradle.properties`）
- 配置缓存已启用

## 依赖添加流程

1. 在 `gradle/libs.versions.toml` 的 `[versions]` 中添加版本号
2. 在 `[libraries]` 中添加 library 引用（`module` + `version.ref`）
3. 在模块 `build.gradle.kts` 中用 `implementation(libs.<alias>)` 引用
4. 如需 plugin，在 `[plugins]` 中注册，根 `build.gradle.kts` 中 apply
