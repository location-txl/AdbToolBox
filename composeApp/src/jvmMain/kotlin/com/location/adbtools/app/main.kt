package com.location.adbtools.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.location.adbtools.adb.EmbeddedAdb

fun main() = application {
    Window(
        onCloseRequest = {
            runCatching { EmbeddedAdb.killServer() }
                .onFailure { error ->
                    System.err.println("关闭 adb 服务失败: ${error.message}")
                }
            exitApplication()
        },
        title = "adb_tools",
    ) {
        App(parentWindow = window)
    }
}
