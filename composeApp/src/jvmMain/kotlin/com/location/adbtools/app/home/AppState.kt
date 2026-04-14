package com.location.adbtools.app.home

import com.location.adbtools.adb.EmbeddedAdb
import com.location.adbtools.device.ConnectedDevice
import com.location.adbtools.filebrowser.FileBrowserUiState
import com.location.adbtools.install.InstallUiState
import com.location.adbtools.transfer.TransferUiState

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
