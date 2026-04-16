package com.location.adbtools.install

import java.io.File

/**
 * APK 安装区域的界面状态。
 *
 * 这里把安装相关字段集中起来，避免主状态继续平铺膨胀。
 *
 * @property apkPath 当前选中的本地 APK 绝对路径。
 * @property statusText 安装区域展示的最近一次结果文案。
 * @property pendingDroppedApkPath 拖拽进入后等待确认安装的 APK 路径。
 * @property isDropInstallConfirmVisible 是否展示拖拽安装确认弹层。
 */
data class InstallUiState(
    val apkPath: String = "",
    val statusText: String = "尚未执行 APK 安装",
    val pendingDroppedApkPath: String? = null,
    val isDropInstallConfirmVisible: Boolean = false,
)

/**
 * 拖拽 APK 文件后的校验结果。
 *
 * @property acceptedApkPath 通过校验的 APK 路径；校验失败时为 null。
 * @property statusText 当前校验结果对应的提示文案。
 */
data class DroppedApkValidationResult(
    val acceptedApkPath: String?,
    val statusText: String,
)

/**
 * 校验拖入窗口的文件列表，只接受单个存在的本地 APK。
 *
 * @param filePaths 拖拽进入窗口的本地路径列表。
 * @return 校验结果；失败时不会返回可安装的 APK 路径。
 */
fun validateDroppedApkFiles(filePaths: List<String>): DroppedApkValidationResult {
    if (filePaths.isEmpty()) {
        return DroppedApkValidationResult(
            acceptedApkPath = null,
            statusText = "未检测到可用的拖拽文件",
        )
    }
    if (filePaths.size != 1) {
        return DroppedApkValidationResult(
            acceptedApkPath = null,
            statusText = "一次只能拖入一个 APK 文件",
        )
    }

    val droppedFile = File(filePaths.single())
    if (!droppedFile.exists() || !droppedFile.isFile) {
        return DroppedApkValidationResult(
            acceptedApkPath = null,
            statusText = "拖入的文件不存在或不是普通文件",
        )
    }
    if (!droppedFile.extension.equals("apk", ignoreCase = true)) {
        return DroppedApkValidationResult(
            acceptedApkPath = null,
            statusText = "只支持拖入 .apk 文件",
        )
    }

    return DroppedApkValidationResult(
        acceptedApkPath = droppedFile.absolutePath,
        statusText = "已拖入 APK：${droppedFile.name}",
    )
}
