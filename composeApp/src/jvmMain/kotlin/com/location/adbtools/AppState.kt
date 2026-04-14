package com.location.adbtools

import com.location.adbtools.adb.EmbeddedAdb
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
 * 文件浏览器区域的界面状态。
 *
 * @property currentRemoteDirectory 当前所在的设备目录。
 * @property remoteEntries 当前目录下的远端条目列表。
 * @property statusText 文件浏览器区域展示的状态文案。
 * @property pendingDeleteEntry 当前等待确认删除的远端条目。
 */
data class FileBrowserUiState(
    val currentRemoteDirectory: String = remoteBrowserRootPath,
    val remoteEntries: List<RemoteFileEntry> = emptyList(),
    val statusText: String = "请先连接设备后再浏览 $remoteBrowserRootPath",
    val pendingDeleteEntry: RemoteFileEntry? = null,
)

/**
 * 上传 / 下载相关的界面状态。
 *
 * @property pullProgressPercent 下载过程中解析出的进度百分比；未知时为 null。
 * @property pullProgressLog 下载过程中最近展示的实时日志。
 * @property pushCurrentFileName 上传过程中当前正在处理的文件名。
 * @property pushProgressPercent 上传过程中解析出的进度百分比；未知时为 null。
 * @property pushProgressLog 上传过程中最近展示的实时日志。
 */
data class TransferUiState(
    val pullProgressPercent: Int? = null,
    val pullProgressLog: String = "等待开始下载",
    val pushCurrentFileName: String? = null,
    val pushProgressPercent: Int? = null,
    val pushProgressLog: String = "等待开始上传",
)

/**
 * 主页面的完整界面状态。
 *
 * 这里遵循官方样例更常见的不可变 UI State 写法：
 * ViewModel 内部只更新快照，对外只暴露只读状态流。
 *
 * @property endpoint 用户输入的设备地址。
 * @property connectedDevices 当前 adb 可操作的设备列表。
 * @property currentSerial 当前选中的设备 serial。
 * @property statusText 页面顶部展示的当前状态文案。
 * @property commandOutput 最近一次 adb 命令的完整输出。
 * @property busyAction 当前正在执行的动作；为空表示空闲。
 * @property install 安装区域状态。
 * @property fileBrowser 文件浏览器区域状态。
 * @property transfer 上传 / 下载区域状态。
 */
data class AppUiState(
    val endpoint: String = "",
    val connectedDevices: List<ConnectedDevice> = emptyList(),
    val currentSerial: String? = null,
    val statusText: String = "等待连接设备",
    val commandOutput: String = "尚未执行命令",
    val busyAction: BusyAction? = null,
    val install: InstallUiState = InstallUiState(),
    val fileBrowser: FileBrowserUiState = FileBrowserUiState(),
    val transfer: TransferUiState = TransferUiState(),
) {

    /**
     * 当前是否存在正在执行的异步动作。
     */
    val isBusy: Boolean
        get() = busyAction != null

    /**
     * 当前选中的设备对象。
     */
    val currentDevice: ConnectedDevice?
        get() = connectedDevices.firstOrNull { it.serial == currentSerial }

    /**
     * 当前是否已经持有有效连接状态。
     */
    val isConnected: Boolean
        get() = currentDevice != null
}

/**
 * 主页面发给 UI 的一次性效果。
 *
 * Snackbar 这类瞬时事件不再混进持久状态，避免重组后重复消费旧值。
 */
sealed interface AppUiEffect {

    /**
     * 请求 UI 展示一条 Snackbar。
     *
     * @property message 当前要展示的文案。
     */
    data class ShowSnackbar(val message: String) : AppUiEffect
}

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
 * 本地上传文件选择结果。
 *
 * @property acceptedFilePaths 通过校验的本地文件绝对路径列表。
 * @property statusText 当前校验结果对应的提示文案。
 */
data class LocalFileSelectionResult(
    val acceptedFilePaths: List<String>,
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

/**
 * 校验本地文件选择结果，只接受存在的普通文件。
 *
 * @param filePaths 用户选择的本地路径列表。
 * @return 经过过滤后的可上传文件列表和对应提示文案。
 */
fun validateSelectedLocalFiles(filePaths: List<String>): LocalFileSelectionResult {
    if (filePaths.isEmpty()) {
        return LocalFileSelectionResult(
            acceptedFilePaths = emptyList(),
            statusText = "未选择要上传的文件",
        )
    }

    val acceptedFiles = mutableListOf<File>()
    var ignoredDirectoryCount = 0
    var ignoredInvalidCount = 0
    filePaths.forEach { filePath ->
        val file = File(filePath)
        when {
            file.isDirectory -> ignoredDirectoryCount += 1
            file.exists() && file.isFile -> acceptedFiles += file
            else -> ignoredInvalidCount += 1
        }
    }

    if (acceptedFiles.isEmpty()) {
        val statusText = when {
            ignoredDirectoryCount > 0 -> "暂不支持上传文件夹"
            else -> "选择的文件不存在或不是普通文件"
        }
        return LocalFileSelectionResult(
            acceptedFilePaths = emptyList(),
            statusText = statusText,
        )
    }

    val baseStatus = if (acceptedFiles.size == 1) {
        "已选择 1 个文件：${acceptedFiles.first().name}"
    } else {
        "已选择 ${acceptedFiles.size} 个文件"
    }
    val ignoredStatus = buildList {
        if (ignoredDirectoryCount > 0) {
            add("已忽略 $ignoredDirectoryCount 个文件夹")
        }
        if (ignoredInvalidCount > 0) {
            add("已忽略 $ignoredInvalidCount 个无效路径")
        }
    }.joinToString(separator = "，")

    return LocalFileSelectionResult(
        acceptedFilePaths = acceptedFiles.map(File::getAbsolutePath),
        statusText = if (ignoredStatus.isBlank()) baseStatus else "$baseStatus，$ignoredStatus",
    )
}

/**
 * 生成文件浏览器区域的摘要文案。
 *
 * @param entries 当前目录条目列表。
 * @return 适合直接展示在界面上的摘要文本。
 */
fun buildRemoteBrowserStatusText(entries: List<RemoteFileEntry>): String {
    if (entries.isEmpty()) {
        return "当前目录为空"
    }

    val directoryCount = entries.count { it.type == RemoteFileType.Directory }
    val fileCount = entries.count { it.type == RemoteFileType.File }
    val hiddenCount = entries.count { it.isHidden }
    return buildString {
        append("共 ${entries.size} 项")
        append("，文件夹 $directoryCount 个")
        append("，文件 $fileCount 个")
        if (hiddenCount > 0) {
            append("，隐藏项 $hiddenCount 个")
        }
    }
}

/**
 * 把 adb 命令结果格式化成适合界面展示的文本。
 *
 * @param result 原始 adb 命令结果。
 * @return 供界面直接展示的多段文本。
 */
fun formatCommandOutput(result: EmbeddedAdb.AdbCommandResult): String {
    return buildString {
        appendLine("exitCode: ${result.exitCode}")
        appendLine()
        appendLine("stdout:")
        appendLine(if (result.stdout.isBlank()) "(empty)" else result.stdout)
        appendLine()
        appendLine("stderr:")
        append(if (result.stderr.isBlank()) "(empty)" else result.stderr)
    }
}
