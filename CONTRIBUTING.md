# Contributing

## 环境

| 依赖 | 版本 | 说明 |
|---|---|---|
| JDK | 25 | server / desktopApp 目标为 JVM 25 字节码；Gradle 工具链可自动下载 |
| Android SDK | targetSdk 37 | 仅编译 Android 目标时需要 |
| Node.js | ≥ 20 | `tiptap-bridge` 构建需要 |
| Docker | 任意近期版本 | 仅容器化部署时需要 |

改不改代码都能跑：服务端默认 H2 内存库 + `http://127.0.0.1:9051`。

## 客户端 API 地址

服务端地址经 BuildKonfig 注入 `core:network`，默认 `127.0.0.1:9051`。需要改时：

```bash
cp network-config.properties.template network-config.properties
# host / port / useTls
```

该文件已 gitignore。生产部署时另需通过环境变量覆盖服务端的 `JWT_SECRET` 与数据库口令（`application.yaml` 里的默认值仅供本地开发）。

## IDE 已知问题：Android/KMP 导入空库

在 IDEA 2026.2.3 环境中混合导入 Android/KMP 与 JVM 模型，会出现同名库合并时保留空 Classes 根的现象：Gradle 编译成功，编辑器仍无法解析 Ktor 等依赖。对应 KTIJ-39840，仅排除 Android Application 入口不足以规避。

受影响时复制 `local-build.properties.example` 为 `local-build.properties`：

```properties
enableAndroid=false
```

该文件被 Git 和 Docker 忽略。关闭模式不应用共享模块的 AGP KMP 插件，不创建 Android targets，也不包含 `:androidApp`；JVM / JS / Wasm 保留。Android 源码不会被删除，但 IDE 无法编辑解析或运行完整 Android 功能。

```bash
./gradlew :desktopApp:compileKotlin -PenableAndroid=false
./gradlew :androidApp:assembleDebug  -PenableAndroid=true
./gradlew check -PenableAndroid=true
```

优先级：Gradle property（`-PenableAndroid=...`）→ 本地配置 → 默认 `true`，只接受 `true` / `false`。旧的 `includeAndroidApp` 仅控制应用入口且默认 `true`。

CI 对 Desktop 验证 Android 开 / 关两种模式，并在开启模式下构建 Android Debug APK；服务端 job 验证完整模式；Docker 服务端构建显式使用 `-PenableAndroid=false`。这个开关是本地 IDE 兼容性规避，不是上游缺陷修复。

## 构建产物

`tiptap-bridge` 的产物已提交到仓库，**只在修改编辑器源码时**才需要重新构建：

```bash
cd tiptap-bridge
npm run build                # UMD → 自动拷贝进 web/src/jsMain/resources
npm run build:webview:editor # 自包含 HTML → 拷贝进 features/article-editor
npm run build:webview:viewer # 自包含 HTML → 拷贝进 features/article
```

`web` 与两个 article feature 的 Gradle 任务已挂接这些 npm 构建，直接跑对应 Gradle 任务也可以。

## 常用命令

| 目的 | 命令 |
|---|---|
| 编译检查（客户端） | `./gradlew :desktopApp:compileKotlin` |
| 全部测试 | `./gradlew check` |
| 服务端测试 | `./gradlew :server:test` |
| 服务端 fat jar | `./gradlew :server:buildFatJar` |
| Compose Web 开发运行 | `./gradlew :webApp:jsBrowserDevelopmentRun` |
| 网页版开发运行 | `./gradlew :web:jsBrowserDevelopmentRun` |
| 网页版生产构建 | `./gradlew :web:jsBrowserDistribution` |
| 列出所有任务 | `./gradlew tasks` |

配置缓存已启用；遇到异常行为加 `--no-configuration-cache`。

## 代码约定

- **提交信息**：`<scope>: <summary>`，scope 取 `client` / `server` / `web` / `core` / `features` / `build` / `deps` / `tiptap`，英文、标题 ≤72 字符
- **代码风格**：Kotlin Official Style，4 空格缩进，显式 import（不用通配符），标识符与注释用英文
- **JVM 目标**：composeApp / Android → 17，desktopApp → 25，server → 25
- **JS 目标**：web → ES2015
- **依赖版本**：`gradle/libs.versions.toml` 是唯一来源，新增依赖先加到 catalog
- **DI**：Koin 注册顺序 `common → datastore → serializers → daos → network → data → domain → feature ViewModels`
- **平台代码**：`expect` / `actual` 分离，非 JS 平台依赖加在 `jvmMain` / `androidMain` / `wasmJsMain`

更详细的模块级说明见 [`notes/`](./notes)（Koin、Navigation 3、自适应布局、测试、版本清单、工作流），AI 协作约定见 [`CLAUDE.md`](./CLAUDE.md)。
