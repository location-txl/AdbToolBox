import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvm()
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.jna)
            implementation(libs.jna.platform)
        }
    }
}

/**
 * 为原生安装包任务固定使用完整 JDK，避免 IDE 自带运行时缺少 `jpackage`。
 */
val packagingJdkHome = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}

/**
 * 安装包版本默认跟随项目配置，发布工作流可通过 `-PpackageVersion=...` 覆盖，
 * 这样 Git tag 与最终生成的安装包文件名能保持一致。
 */
val desktopPackageVersion = providers.gradleProperty("packageVersion").orElse("1.0.0")


compose.desktop {
    application {
        mainClass = "com.location.adbtools.MainKt"
        javaHome = packagingJdkHome.get().metadata.installationPath.asFile.absolutePath

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "adb_tools"
            packageVersion = desktopPackageVersion.get()
            windows {
                menu = true
                menuGroup = "Adb Tools"
                shortcut = true
            }
        }
    }
}
