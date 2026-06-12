# 测试配置与命令

## 测试框架

| 框架 | 用途 |
|---|---|
| `kotlin.test` | KMP 跨平台测试（所有模块 commonTest） |
| JUnit 4 | Android 单元测试 / Instrumentation 测试 |
| `ktor-server-test-host` | 服务端集成测试 |

已声明但未使用：`koin-test`、`androidx.paging-testing`

## 全局命令

| 目的 | 命令 |
|---|---|
| 运行全部测试 | `./gradlew check` |
| 服务端测试 | `./gradlew :server:test` |
| 编译检查（客户端） | `./gradlew :composeApp:jvmMainClasses` |

## 各模块测试配置

### 客户端核心模块（有 commonTest 依赖但无测试文件）

以下模块已在 `build.gradle.kts` 中声明 `commonTest` 依赖 `kotlin.test`，可通过添加 `src/commonTest/kotlin/` 下的测试文件开始编写测试：

| 模块 | 测试依赖 |
|---|---|
| `core:model` | `kotlin.test` |
| `core:data` | `kotlin.test` |
| `core:network` | `kotlin.test` |
| `core:database` | `kotlin.test` |
| `core:datastore` | `kotlin.test` |
| `core:domain` | `kotlin.test` |
| `core:ui` | `kotlin.test` |
| `composeApp` | `kotlin.test` |

### 服务端测试

```kotlin
// server/src/test/kotlin/.../ApplicationTest.kt
class ApplicationTest {
    @Test
    fun testRoot() = testApplication {
        application { module() }
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }
}
```

运行：
```bash
./gradlew :server:test
```

### WebSocket 集成测试

```bash
./gradlew :server:wsTest  # 运行 WebSocket 消息集成测试
```

### Android 测试

```bash
# 单元测试（JVM）
./gradlew :androidApp:test

# Instrumentation 测试（需要模拟器/真机）
./gradlew :androidApp:connectedAndroidTest
```

Android 测试配置：
- `testInstrumentationRunner` = `androidx.test.runner.AndroidJUnitRunner`
- `testOptions.unitTests.isIncludeAndroidResources` = `true`

## 编写测试的约定

### KMP 跨平台测试

```kotlin
// src/commonTest/kotlin/.../MyTest.kt
import kotlin.test.Test
import kotlin.test.assertEquals

class MyTest {
    @Test
    fun testSomething() {
        assertEquals(4, 2 + 2)
    }
}
```

### 服务端 Route 测试

```kotlin
@Test
fun testRoute() = testApplication {
    application { module() }
    val response = client.get("/v1/users/check?email=test@test.com")
    assertEquals(HttpStatusCode.OK, response.status)
}
```

### 需要 Koin 的测试

```kotlin
// 使用 koin-test
class MyFeatureTest : KoinTest {
    @BeforeTest
    fun setup() {
        startKoin {
            modules(testModule)
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }
}
```

## 当前测试状态

- 客户端：8 个模块已配好测试依赖，但**零测试文件**
- 服务端：仅 1 个占位测试
- Android：2 个占位测试
- Feature 模块：**无测试配置**——需要添加 `commonTest` source set 和 `kotlin.test` 依赖
