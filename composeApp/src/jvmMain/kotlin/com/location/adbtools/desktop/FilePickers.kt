package com.location.adbtools.desktop

import java.awt.Window

/**
 * 打开系统目录选择框，返回用户选中的目录绝对路径。
 */
fun selectDirectory(parentWindow: Window?): String? {
    return NativeDirectoryPicker.pickDirectory(parentWindow)
}

/**
 * 打开系统文件选择框，返回用户选中的 APK 绝对路径。
 */
fun selectApkFile(parentWindow: Window?): String? {
    return NativeDirectoryPicker.pickApkFile(parentWindow)
}

/**
 * 打开系统文件选择框，返回用户选择的多个本地文件绝对路径。
 */
fun selectLocalFiles(parentWindow: Window?): List<String> {
    return NativeDirectoryPicker.pickFiles(parentWindow)
}
