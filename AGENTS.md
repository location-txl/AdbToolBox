# Repository Guidelines

## 项目结构与模块组织

这是一个 Kotlin Multiplatform 桌面应用，使用 Compose for Desktop，主模块是 `composeApp`。

- `composeApp/src/jvmMain/kotlin/com/location/adbtools`：JVM 桌面端源码。
- `composeApp/src/jvmMain/resources`：运行时资源，包括 `adb/<platform>` 下的内置 ADB 文件。
- `composeApp/src/jvmMain/composeResources`：Compose 管理的 UI 资源。
- `composeApp/src/jvmTest/kotlin`：JVM 单元测试。
- `gradle/libs.versions.toml`：依赖和 Gradle 插件版本。
- `.github/workflows`：发布和 CI 自动化配置。

代码按业务功能组织。相关状态、UI、弹窗和模型应就近放置，例如 `filebrowser/`、`install/`、`transfer/`、`audio/`。

## 构建、测试与本地开发命令

- `./gradlew :composeApp:run`：本地运行桌面应用。
- `./gradlew :composeApp:jvmTest`：运行 JVM 单元测试。
- `./gradlew :composeApp:check`：执行模块校验任务。
- `./gradlew :composeApp:packageDmg`：构建 macOS 安装包。
- `./gradlew :composeApp:packageMsi`：构建 Windows 安装包。

原生安装包依赖包含 `jpackage` 的 JDK；当前构建配置使用 Java 21 toolchain。

## 编码风格与命名约定

使用 Kotlin 惯用写法，保持代码直接、清晰。优先按功能拆小文件，避免无收益的共享抽象。缩进使用 4 个空格；类和 Composable 使用 `PascalCase`；函数和属性使用 `camelCase`；只有真正的常量使用 `UPPER_SNAKE_CASE`。

Composable UI 函数应按渲染内容命名，例如 `InstallSection`、`DeleteRemoteEntryConfirmDialog`。状态对象使用清晰名称，例如 `InstallState`、`TransferState`。

新增公共类和重要 API 需要写 doc 注释。明显的私有辅助函数不要机械添加注释。

## 严格遵守
- 执行 任何 `./gradlew **` 命令时 必须提权执行, 严禁指定 `GRADLE_USER_HOME` 
