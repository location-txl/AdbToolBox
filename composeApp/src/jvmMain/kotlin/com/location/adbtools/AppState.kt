package com.location.adbtools

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.location.adbtools.adb.EmbeddedAdb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 主页面的界面状态。
 *
 * 这里继续集中保存页面上的可变状态，
 * 但文件相关能力已经从“手填路径表单”切成“远端文件浏览器”。
 */
@Stable
class AppUiState {

    /** 用户输入的设备地址，例如 `192.168.1.10:5555`。 */
    var endpoint by mutableStateOf("")

    /** 当前选中的本地 APK 绝对路径。 */
    var apkPath by mutableStateOf("")

    /** 当前已连接设备的 serial；未连接时为 null。 */
    var currentSerial by mutableStateOf<String?>(null)

    /** 当前 adb 可直接操作的已连接设备列表。 */
    var connectedDevices by mutableStateOf(emptyList<ConnectedDevice>())

    /** 页面顶部展示的当前状态文案。 */
    var statusText by mutableStateOf("等待连接设备")

    /** 安装 APK 区域展示的最近一次安装结果文案。 */
    var installStatusText by mutableStateOf("尚未执行 APK 安装")

    /** 最近一次 adb 命令的完整输出。 */
    var commandOutput by mutableStateOf("尚未执行命令")

    /** 当前是否存在正在执行的异步动作。 */
    var isBusy by mutableStateOf(false)

    /** 当前具体在忙什么，用于控制不同 loading 表现。 */
    var busyAction by mutableStateOf<BusyAction?>(null)

    /** 下载过程中当前解析出的进度百分比；未知时为 null。 */
    var pullProgressPercent by mutableStateOf<Int?>(null)

    /** 下载过程中最近展示的实时输出日志。 */
    var pullProgressLog by mutableStateOf("等待开始下载")

    /** 上传过程中当前正在处理的文件名。 */
    var pushCurrentFileName by mutableStateOf<String?>(null)

    /** 上传过程中当前解析出的进度百分比；未知时为 null。 */
    var pushProgressPercent by mutableStateOf<Int?>(null)

    /** 上传过程中最近展示的实时输出日志。 */
    var pushProgressLog by mutableStateOf("等待开始上传")

    /** 成功操作后供界面消费的一次性 Snackbar 文案。 */
    var snackbarMessage by mutableStateOf<String?>(null)

    /** 拖拽进入后等待确认安装的 APK 绝对路径。 */
    var pendingDroppedApkPath by mutableStateOf<String?>(null)

    /** 是否展示拖拽 APK 的确认弹窗。 */
    var isDropInstallConfirmVisible by mutableStateOf(false)

    /** 文件浏览器当前所在的设备目录。 */
    var currentRemoteDirectory by mutableStateOf(remoteBrowserRootPath)

    /** 当前目录下的远端条目列表。 */
    var remoteEntries by mutableStateOf(emptyList<RemoteFileEntry>())

    /** 文件浏览器区域展示的最近一次状态文案。 */
    var fileBrowserStatusText by mutableStateOf("请先连接设备后再浏览 $remoteBrowserRootPath")

    /** 当前等待用户确认删除的远端条目。 */
    var pendingDeleteEntry by mutableStateOf<RemoteFileEntry?>(null)

    /** 当前是否已经持有有效连接状态。 */
    val isConnected: Boolean
        get() = currentDevice != null

    /** 当前选中的设备对象。 */
    val currentDevice: ConnectedDevice?
        get() = connectedDevices.firstOrNull { it.serial == currentSerial }

    /**
     * 取出当前待展示的 Snackbar 文案，并立刻清空，避免重组后重复弹出。
     */
    fun consumeSnackbarMessage(): String? {
        val message = snackbarMessage
        snackbarMessage = null
        return message
    }
}

/**
 * 主页面 ViewModel。
 *
 * 这里集中管理桌面主页面的状态流转和 adb 异步调用，
 * UI 只负责发事件和处理系统级文件选择框。
 */
class AdbToolsViewModel : ViewModel() {

    /** 当前页面状态对象，供 Compose 直接读取。 */
    val uiState = AppUiState()

    /**
     * 更新设备地址输入值。
     */
    fun updateEndpoint(value: String) {
        uiState.endpoint = value
    }

    /**
     * 切换当前选中的设备。
     *
     * 设备切换后会立即刷新当前目录，避免浏览器还停留在上一台设备的旧列表。
     */
    fun selectDevice(serial: String) {
        val targetDevice = uiState.connectedDevices.firstOrNull { it.serial == serial } ?: return
        uiState.currentSerial = targetDevice.serial
        uiState.statusText = "已选择设备：${targetDevice.displayName}"
        refreshRemoteDirectory()
    }

    /**
     * 记录用户挑选的 APK 文件。
     *
     * 文件选择框仍在 UI 层触发，避免 ViewModel 持有桌面窗口对象。
     */
    fun updateSelectedApkPath(selectedApkPath: String) {
        uiState.apkPath = selectedApkPath
        clearPendingDroppedApk()
        uiState.installStatusText = "已选择 APK：${File(selectedApkPath).name}"
    }

    /**
     * 处理窗口拖入的文件列表，只接受单个 APK 文件。
     */
    fun handleDroppedApkFiles(filePaths: List<String>) {
        val validationResult = validateDroppedApkFiles(filePaths)
        uiState.installStatusText = validationResult.statusText
        val acceptedApkPath = validationResult.acceptedApkPath
        if (acceptedApkPath == null) {
            clearPendingDroppedApk()
            return
        }

        uiState.apkPath = acceptedApkPath
        uiState.pendingDroppedApkPath = acceptedApkPath
        uiState.isDropInstallConfirmVisible = true
    }

    /**
     * 用户确认后继续执行拖拽来源的 APK 安装。
     *
     * 这里复用现有安装流程，避免额外分叉第二套 adb install 逻辑。
     */
    fun confirmDroppedApkInstall() {
        val pendingApkPath = uiState.pendingDroppedApkPath ?: return
        uiState.apkPath = pendingApkPath
        clearPendingDroppedApk()
        installApk()
    }

    /**
     * 取消拖拽安装确认，只关闭确认态。
     */
    fun dismissDroppedApkInstall() {
        clearPendingDroppedApk()
        uiState.installStatusText = "已取消拖拽安装"
    }

    /**
     * 连接用户指定的设备地址。
     */
    fun connectDevice(endpointInput: String = uiState.endpoint) {
        val trimmedEndpoint = endpointInput.trim()
        if (trimmedEndpoint.isEmpty()) {
            uiState.statusText = "设备地址不能为空"
            return
        }
        uiState.isBusy = true
        uiState.busyAction = BusyAction.Connecting
        uiState.statusText = "正在连接设备..."
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                EmbeddedAdb.connect(trimmedEndpoint)
            }
            uiState.commandOutput = formatCommandOutput(result)
            if (EmbeddedAdb.isConnectSuccess(result)) {
                val connectedDevice = buildConnectedDevice(trimmedEndpoint)
                uiState.endpoint = trimmedEndpoint
                uiState.connectedDevices = listOf(connectedDevice)
                uiState.currentSerial = connectedDevice.serial
                refreshDevicesInternal(
                    preferredSerial = trimmedEndpoint,
                    successStatus = "连接成功：$trimmedEndpoint",
                    failureStatus = "连接成功：$trimmedEndpoint，但设备列表刷新失败",
                    clearStaleDevicesOnFailure = false,
                    updateCommandOutput = false,
                )
                syncRemoteBrowserWithCurrentDevice()
            } else {
                uiState.statusText = result.stderr.ifBlank { "连接失败" }
            }
            uiState.busyAction = null
            uiState.isBusy = false
        }
    }

    /**
     * 断开当前已连接设备。
     */
    fun disconnectDevice(serialInput: String? = uiState.currentSerial) {
        val serial = serialInput ?: return
        val targetDevice = uiState.connectedDevices.firstOrNull { it.serial == serial }
        if (targetDevice?.connectionType != DeviceConnectionType.Network) {
            uiState.statusText = "当前选中的是 USB 设备，不能断开"
            return
        }
        uiState.isBusy = true
        uiState.busyAction = BusyAction.Disconnecting
        uiState.statusText = "正在断开设备..."
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                EmbeddedAdb.disconnect(serial)
            }
            uiState.commandOutput = formatCommandOutput(result)
            if (result.exitCode == 0) {
                refreshDevicesInternal(
                    preferredSerial = null,
                    successStatus = "已断开：$serial",
                    updateCommandOutput = false,
                )
                syncRemoteBrowserWithCurrentDevice()
            } else {
                uiState.statusText = result.stderr.ifBlank { "断开失败" }
            }
            uiState.busyAction = null
            uiState.isBusy = false
        }
    }

    /**
     * 刷新当前 adb 已连接设备列表。
     */
    fun refreshDevices() {
        uiState.isBusy = true
        uiState.busyAction = BusyAction.Refreshing
        uiState.statusText = "正在刷新设备列表..."
        viewModelScope.launch {
            refreshDevicesInternal(
                preferredSerial = uiState.currentSerial,
                updateCommandOutput = true,
            )
            syncRemoteBrowserWithCurrentDevice()
            uiState.busyAction = null
            uiState.isBusy = false
        }
    }

    /**
     * 刷新当前设备目录。
     */
    fun refreshRemoteDirectory() {
        browseRemoteDirectory(
            targetDirectory = uiState.currentRemoteDirectory,
            successStatus = "已刷新目录：${uiState.currentRemoteDirectory}",
        )
    }

    /**
     * 进入某个文件夹。
     */
    fun openRemoteEntry(entry: RemoteFileEntry) {
        if (entry.type != RemoteFileType.Directory) {
            return
        }
        browseRemoteDirectory(
            targetDirectory = entry.path,
            successStatus = "已进入目录：${entry.path}",
        )
    }

    /**
     * 返回上一级目录；根目录 `/sdcard` 不再继续上跳。
     */
    fun navigateToParentRemoteDirectory() {
        val parentPath = parentRemotePath(uiState.currentRemoteDirectory) ?: return
        browseRemoteDirectory(
            targetDirectory = parentPath,
            successStatus = "已返回目录：$parentPath",
        )
    }

    /**
     * 下载右键命中的文件或文件夹到指定本地目录。
     *
     * 本地目录由 UI 先选好再传入，避免 ViewModel 直接依赖桌面窗口。
     */
    fun downloadRemoteEntryToDirectory(entry: RemoteFileEntry, selectedDirectory: String?) {
        val serial = uiState.currentDevice?.serial
        if (serial.isNullOrEmpty()) {
            uiState.statusText = "请先连接设备"
            uiState.fileBrowserStatusText = "请先连接设备后再下载"
            return
        }

        if (selectedDirectory.isNullOrBlank()) {
            uiState.fileBrowserStatusText = "已取消选择本地保存目录"
            return
        }

        uiState.isBusy = true
        uiState.busyAction = BusyAction.Pulling
        uiState.pullProgressPercent = null
        uiState.pullProgressLog = "正在准备下载：${entry.name}"
        uiState.statusText = "正在下载：${entry.name}"
        uiState.fileBrowserStatusText = "正在下载到本地目录：$selectedDirectory"
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                EmbeddedAdb.pull(
                    serial = serial,
                    remotePath = entry.path,
                    localPath = selectedDirectory,
                    onProgress = { progress ->
                        viewModelScope.launch {
                            uiState.pullProgressPercent =
                                progress.percent ?: uiState.pullProgressPercent
                            uiState.pullProgressLog = appendTransferProgressLog(
                                currentLog = uiState.pullProgressLog,
                                latestLine = progress.latestLine,
                            )
                        }
                    },
                )
            }
            uiState.commandOutput = formatCommandOutput(result)
            if (result.exitCode == 0) {
                uiState.snackbarMessage = if (entry.type == RemoteFileType.Directory) {
                    "文件夹下载完成"
                } else {
                    "文件下载完成"
                }
                uiState.statusText = "已下载：${entry.name}"
                uiState.fileBrowserStatusText = "已下载到：$selectedDirectory"
                refreshRemoteDirectorySnapshot(serial)
            } else {
                val errorMessage = result.stderr.ifBlank { "下载失败" }
                uiState.statusText = errorMessage
                uiState.fileBrowserStatusText = errorMessage
            }
            uiState.busyAction = null
            uiState.isBusy = false
        }
    }

    /**
     * 向右键命中的位置上传用户已选中的本地文件。
     *
     * 文件路径由 UI 层负责选择，ViewModel 只做校验和上传流程。
     */
    fun uploadSelectedLocalFiles(entry: RemoteFileEntry, filePaths: List<String>) {
        val serial = uiState.currentDevice?.serial
        if (serial.isNullOrEmpty()) {
            uiState.statusText = "请先连接设备"
            uiState.fileBrowserStatusText = "请先连接设备后再上传"
            return
        }

        val validationResult = validateSelectedLocalFiles(filePaths)
        if (validationResult.acceptedFilePaths.isEmpty()) {
            uiState.fileBrowserStatusText = validationResult.statusText
            return
        }

        val targetDirectory = resolveUploadTargetDirectory(entry)
        uiState.isBusy = true
        uiState.busyAction = BusyAction.Pushing
        uiState.pushCurrentFileName = null
        uiState.pushProgressPercent = null
        uiState.pushProgressLog = "正在准备上传到：$targetDirectory"
        uiState.statusText = "正在上传文件..."
        uiState.fileBrowserStatusText = "目标目录：$targetDirectory"
        viewModelScope.launch {
            var lastResult: EmbeddedAdb.AdbCommandResult? = null
            var successCount = 0
            var failedFileName: String? = null

            for (filePath in validationResult.acceptedFilePaths) {
                val fileName = File(filePath).name
                uiState.pushCurrentFileName = fileName
                uiState.pushProgressPercent = null
                uiState.pushProgressLog = appendTransferProgressLog(
                    currentLog = uiState.pushProgressLog,
                    latestLine = "开始上传：$fileName",
                )
                val result = withContext(Dispatchers.IO) {
                    EmbeddedAdb.push(
                        serial = serial,
                        localPath = filePath,
                        remotePath = targetDirectory,
                        onProgress = { progress ->
                            viewModelScope.launch {
                                uiState.pushProgressPercent =
                                    progress.percent ?: uiState.pushProgressPercent
                                uiState.pushProgressLog = appendTransferProgressLog(
                                    currentLog = uiState.pushProgressLog,
                                    latestLine = progress.latestLine,
                                )
                            }
                        },
                    )
                }
                lastResult = result
                if (result.exitCode != 0) {
                    failedFileName = fileName
                    break
                }
                successCount += 1
            }

            val finalResult = lastResult
            if (finalResult != null) {
                uiState.commandOutput = formatCommandOutput(finalResult)
            }

            if (finalResult != null && finalResult.exitCode == 0) {
                uiState.snackbarMessage = "文件上传完成"
                uiState.statusText = "成功上传 $successCount 个文件"
                uiState.fileBrowserStatusText = "已上传到：$targetDirectory"
                refreshRemoteDirectorySnapshot(serial)
            } else {
                val errorMessage = finalResult?.stderr
                    ?.ifBlank { null }
                    ?: "上传失败：${failedFileName ?: "未知文件"}"
                uiState.statusText = errorMessage
                uiState.fileBrowserStatusText = errorMessage
            }

            uiState.busyAction = null
            uiState.isBusy = false
        }
    }

    /**
     * 打开删除确认态。
     */
    fun requestDeleteRemoteEntry(entry: RemoteFileEntry) {
        if (!uiState.isConnected) {
            uiState.statusText = "请先连接设备"
            return
        }
        uiState.pendingDeleteEntry = entry
    }

    /**
     * 关闭删除确认态。
     */
    fun dismissDeleteRemoteEntry() {
        uiState.pendingDeleteEntry = null
    }

    /**
     * 确认删除远端文件或目录。
     */
    fun confirmDeleteRemoteEntry() {
        val targetEntry = uiState.pendingDeleteEntry ?: return
        val serial = uiState.currentDevice?.serial
        uiState.pendingDeleteEntry = null
        if (serial.isNullOrEmpty()) {
            uiState.statusText = "请先连接设备"
            uiState.fileBrowserStatusText = "请先连接设备后再删除"
            return
        }

        uiState.isBusy = true
        uiState.busyAction = BusyAction.Deleting
        uiState.statusText = "正在删除：${targetEntry.name}"
        uiState.fileBrowserStatusText = "正在删除：${targetEntry.path}"
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                EmbeddedAdb.deleteRemoteEntry(
                    serial = serial,
                    remotePath = targetEntry.path,
                    isDirectory = targetEntry.type == RemoteFileType.Directory,
                )
            }
            uiState.commandOutput = formatCommandOutput(result)
            if (result.exitCode == 0) {
                uiState.snackbarMessage = "已删除：${targetEntry.name}"
                uiState.statusText = "已删除：${targetEntry.name}"
                refreshRemoteDirectorySnapshot(serial)
            } else {
                val errorMessage = result.stderr.ifBlank { "删除失败" }
                uiState.statusText = errorMessage
                uiState.fileBrowserStatusText = errorMessage
            }
            uiState.busyAction = null
            uiState.isBusy = false
        }
    }

    /**
     * 在当前选中的设备上安装单个 APK。
     *
     * 这里固定使用 `adb install -r`，只处理当前需求要求的覆盖安装。
     */
    fun installApk() {
        val serial = uiState.currentDevice?.serial
        val trimmedApkPath = uiState.apkPath.trim()
        when {
            serial.isNullOrEmpty() -> {
                uiState.installStatusText = "请先连接设备后再安装 APK"
                return
            }

            trimmedApkPath.isEmpty() -> {
                uiState.installStatusText = "请先选择 APK 文件"
                return
            }
        }
        clearPendingDroppedApk()
        uiState.isBusy = true
        uiState.busyAction = BusyAction.Installing
        uiState.installStatusText = "正在安装 APK..."
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                EmbeddedAdb.installApk(
                    serial = serial,
                    apkPath = trimmedApkPath,
                )
            }
            uiState.commandOutput = formatCommandOutput(result)
            uiState.installStatusText = if (result.exitCode == 0) {
                uiState.snackbarMessage = "APK 安装完成"
                "APK 安装成功"
            } else {
                result.stderr.ifBlank { "APK 安装失败" }
            }
            uiState.busyAction = null
            uiState.isBusy = false
        }
    }

    /**
     * 执行一次设备列表刷新，并按优先顺序恢复当前选择。
     */
    private suspend fun refreshDevicesInternal(
        preferredSerial: String?,
        successStatus: String? = null,
        failureStatus: String? = null,
        clearStaleDevicesOnFailure: Boolean = true,
        updateCommandOutput: Boolean,
    ) {
        val deviceListResult = withContext(Dispatchers.IO) {
            EmbeddedAdb.listDevices()
        }
        if (updateCommandOutput) {
            uiState.commandOutput = formatCommandOutput(deviceListResult.commandResult)
        }
        if (deviceListResult.commandResult.exitCode != 0) {
            if (clearStaleDevicesOnFailure) {
                uiState.connectedDevices = emptyList()
                uiState.currentSerial = null
                clearRemoteBrowser("设备列表刷新失败，暂时无法浏览文件")
            }
            uiState.statusText =
                failureStatus ?: deviceListResult.commandResult.stderr.ifBlank { "刷新设备失败" }
            return
        }

        val devices = deviceListResult.devices
        uiState.connectedDevices = devices
        val nextSelectedDevice = resolveNextSelectedDevice(
            devices = devices,
            preferredSerial = preferredSerial,
            currentSerial = uiState.currentSerial,
        )
        uiState.currentSerial = nextSelectedDevice?.serial
        uiState.statusText = when {
            successStatus != null -> successStatus
            devices.isEmpty() -> "未检测到已连接设备"
            else -> "已刷新设备列表，共 ${devices.size} 台"
        }
    }

    /**
     * 统一进入设备目录浏览流程。
     */
    private fun browseRemoteDirectory(targetDirectory: String, successStatus: String) {
        val serial = uiState.currentDevice?.serial
        if (serial.isNullOrEmpty()) {
            uiState.statusText = "请先连接设备"
            uiState.fileBrowserStatusText = "请先连接设备后再浏览 $remoteBrowserRootPath"
            return
        }

        val normalizedTargetDirectory = normalizeRemotePath(targetDirectory)
        uiState.isBusy = true
        uiState.busyAction = BusyAction.BrowsingFiles
        uiState.statusText = "正在读取目录：$normalizedTargetDirectory"
        uiState.fileBrowserStatusText = "正在加载：$normalizedTargetDirectory"
        viewModelScope.launch {
            refreshRemoteDirectoryInternal(
                serial = serial,
                directoryPath = normalizedTargetDirectory,
                updateCommandOutput = true,
                successStatus = successStatus,
                clearEntriesOnFailure = false,
                showFailureInStatus = true,
            )
            uiState.busyAction = null
            uiState.isBusy = false
        }
    }

    /**
     * 在设备切换、连接成功或设备列表刷新后同步浏览器内容。
     *
     * 这里不覆盖顶部状态文案，让设备相关动作继续保留自己的结果提示。
     */
    private suspend fun syncRemoteBrowserWithCurrentDevice() {
        val currentDevice = uiState.currentDevice
        if (currentDevice == null) {
            clearRemoteBrowser("请先连接设备后再浏览 $remoteBrowserRootPath")
            return
        }
        refreshRemoteDirectoryInternal(
            serial = currentDevice.serial,
            directoryPath = uiState.currentRemoteDirectory,
            updateCommandOutput = false,
            successStatus = null,
            clearEntriesOnFailure = true,
            showFailureInStatus = false,
        )
    }

    /**
     * 在上传、下载、删除成功后静默刷新当前目录。
     *
     * 操作结果优先展示给用户，因此这里不覆盖顶部状态和命令输出。
     */
    private suspend fun refreshRemoteDirectorySnapshot(serial: String) {
        refreshRemoteDirectoryInternal(
            serial = serial,
            directoryPath = uiState.currentRemoteDirectory,
            updateCommandOutput = false,
            successStatus = null,
            clearEntriesOnFailure = false,
            showFailureInStatus = false,
        )
    }

    /**
     * 真正执行远端目录刷新，并按调用场景决定是否更新状态栏。
     */
    private suspend fun refreshRemoteDirectoryInternal(
        serial: String,
        directoryPath: String,
        updateCommandOutput: Boolean,
        successStatus: String?,
        clearEntriesOnFailure: Boolean,
        showFailureInStatus: Boolean,
    ): Boolean {
        val normalizedDirectoryPath = normalizeRemotePath(directoryPath)
        val result = withContext(Dispatchers.IO) {
            EmbeddedAdb.listRemoteEntries(serial, normalizedDirectoryPath)
        }
        if (updateCommandOutput) {
            uiState.commandOutput = formatCommandOutput(result.commandResult)
        }
        if (result.commandResult.exitCode != 0) {
            if (clearEntriesOnFailure) {
                uiState.remoteEntries = emptyList()
            }
            val errorMessage = result.commandResult.stderr.ifBlank { "读取目录失败" }
            uiState.fileBrowserStatusText = errorMessage
            if (showFailureInStatus) {
                uiState.statusText = errorMessage
            }
            return false
        }

        uiState.currentRemoteDirectory = normalizedDirectoryPath
        uiState.remoteEntries = result.entries
        uiState.fileBrowserStatusText = buildRemoteBrowserStatusText(result.entries)
        if (successStatus != null) {
            uiState.statusText = successStatus
        }
        return true
    }

    /**
     * 计算刷新设备列表后的新选择。
     */
    private fun resolveNextSelectedDevice(
        devices: List<ConnectedDevice>,
        preferredSerial: String?,
        currentSerial: String?,
    ): ConnectedDevice? {
        if (devices.isEmpty()) {
            return null
        }
        preferredSerial?.let { serial ->
            devices.firstOrNull { it.serial == serial }?.let { return it }
        }
        currentSerial?.let { serial ->
            devices.firstOrNull { it.serial == serial }?.let { return it }
        }
        return devices.first()
    }

    /**
     * 清理拖拽确认态，避免旧的确认框残留。
     */
    private fun clearPendingDroppedApk() {
        uiState.pendingDroppedApkPath = null
        uiState.isDropInstallConfirmVisible = false
    }

    /**
     * 清空文件浏览器内容。
     */
    private fun clearRemoteBrowser(statusText: String) {
        uiState.currentRemoteDirectory = remoteBrowserRootPath
        uiState.remoteEntries = emptyList()
        uiState.pendingDeleteEntry = null
        uiState.fileBrowserStatusText = statusText
    }
}

/**
 * 拖拽 APK 文件后的校验结果。
 */
data class DroppedApkValidationResult(
    val acceptedApkPath: String?,
    val statusText: String,
)

/**
 * 本地上传文件选择结果。
 */
data class LocalFileSelectionResult(
    val acceptedFilePaths: List<String>,
    val statusText: String,
)

/**
 * 校验拖入窗口的文件列表，只接受单个存在的本地 APK。
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
 */
private fun formatCommandOutput(result: EmbeddedAdb.AdbCommandResult): String {
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
