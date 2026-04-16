package com.location.adbtools.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.location.adbtools.adb.AdbGateway
import com.location.adbtools.adb.EmbeddedAdb
import com.location.adbtools.device.ConnectedDevice
import com.location.adbtools.device.DeviceConnectionType
import com.location.adbtools.device.buildConnectedDevice
import com.location.adbtools.filebrowser.FileBrowserUiState
import com.location.adbtools.filebrowser.RemoteFileEntry
import com.location.adbtools.filebrowser.RemoteFileType
import com.location.adbtools.filebrowser.buildRemoteBrowserStatusText
import com.location.adbtools.filebrowser.normalizeRemotePath
import com.location.adbtools.filebrowser.parentRemotePath
import com.location.adbtools.filebrowser.remoteBrowserRootPath
import com.location.adbtools.filebrowser.resolveUploadTargetDirectory
import com.location.adbtools.install.InstallUiState
import com.location.adbtools.install.validateDroppedApkFiles
import com.location.adbtools.transfer.TransferUiState
import com.location.adbtools.transfer.appendTransferProgressLog
import com.location.adbtools.transfer.validateSelectedLocalFiles
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 主页面 ViewModel。
 *
 * 这里统一接住页面事件、驱动 adb 异步调用，并把结果收敛成只读 UI State。
 *
 * @property adbGateway 当前桌面端 adb 调用入口。
 * @property ioDispatcher 执行 adb / 文件相关阻塞操作的协程调度器。
 */
class AdbToolsViewModel(
    private val adbGateway: AdbGateway,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())

    /**
     * 当前页面状态快照，只允许 UI 读取。
     */
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AppUiEffect>(extraBufferCapacity = 1)

    /**
     * 页面一次性效果流，用于 Snackbar 这类瞬时事件。
     */
    val effects: SharedFlow<AppUiEffect> = _effects.asSharedFlow()

    /**
     * 更新设备地址输入值。
     *
     * @param value 用户最新输入的设备地址。
     */
    fun updateEndpoint(value: String) {
        _uiState.update { state ->
            state.copy(endpoint = value)
        }
    }

    /**
     * 切换当前选中的设备。
     *
     * 设备切换后会立即刷新当前目录，避免浏览器仍停留在旧设备的快照上。
     *
     * @param serial 用户选择的设备 serial。
     */
    fun selectDevice(serial: String) {
        val targetDevice = uiState.value.connectedDevices.firstOrNull { it.serial == serial } ?: return
        _uiState.update { state ->
            state.copy(
                currentSerial = targetDevice.serial,
                statusText = "已选择设备：${targetDevice.displayName}",
            )
        }
        refreshRemoteDirectory()
    }

    /**
     * 记录用户挑选的 APK 文件。
     *
     * 文件选择框仍保留在 UI 层触发，避免 ViewModel 依赖桌面窗口对象。
     *
     * @param selectedApkPath 用户选择的 APK 绝对路径。
     */
    fun updateSelectedApkPath(selectedApkPath: String) {
        _uiState.update { state ->
            state.withInstall {
                it.copy(
                    apkPath = selectedApkPath,
                    statusText = "已选择 APK：${File(selectedApkPath).name}",
                    pendingDroppedApkPath = null,
                    isDropInstallConfirmVisible = false,
                )
            }
        }
    }

    /**
     * 处理窗口拖入的文件列表，只接受单个 APK 文件。
     *
     * @param filePaths 当前拖入窗口的本地文件路径列表。
     */
    fun handleDroppedApkFiles(filePaths: List<String>) {
        val validationResult = validateDroppedApkFiles(filePaths)
        _uiState.update { state ->
            val baseState = state.withInstall {
                it.copy(statusText = validationResult.statusText)
            }
            val acceptedApkPath = validationResult.acceptedApkPath
            if (acceptedApkPath == null) {
                return@update baseState.withInstall {
                    it.copy(
                        pendingDroppedApkPath = null,
                        isDropInstallConfirmVisible = false,
                    )
                }
            }
            baseState.withInstall {
                it.copy(
                    apkPath = acceptedApkPath,
                    pendingDroppedApkPath = acceptedApkPath,
                    isDropInstallConfirmVisible = true,
                )
            }
        }
    }

    /**
     * 用户确认后继续执行拖拽来源的 APK 安装。
     *
     * 这里复用现有安装流程，避免分叉第二套安装逻辑。
     */
    fun confirmDroppedApkInstall() {
        val pendingApkPath = uiState.value.install.pendingDroppedApkPath ?: return
        _uiState.update { state ->
            state.withInstall {
                it.copy(
                    apkPath = pendingApkPath,
                    pendingDroppedApkPath = null,
                    isDropInstallConfirmVisible = false,
                )
            }
        }
        installApk()
    }

    /**
     * 取消拖拽安装确认，只关闭确认态。
     */
    fun dismissDroppedApkInstall() {
        _uiState.update { state ->
            state.withInstall {
                it.copy(
                    statusText = "已取消拖拽安装",
                    pendingDroppedApkPath = null,
                    isDropInstallConfirmVisible = false,
                )
            }
        }
    }

    /**
     * 连接用户指定的设备地址。
     *
     * @param endpointInput 待连接的设备地址；默认使用当前输入框内容。
     */
    fun connectDevice(endpointInput: String = uiState.value.endpoint) {
        val trimmedEndpoint = endpointInput.trim()
        if (trimmedEndpoint.isEmpty()) {
            _uiState.update { state ->
                state.copy(statusText = "设备地址不能为空")
            }
            return
        }

        _uiState.update { state ->
            state.copy(
                statusText = "正在连接设备...",
                busyAction = BusyAction.Connecting,
            )
        }

        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                adbGateway.connect(trimmedEndpoint)
            }
            _uiState.update { state ->
                state.copy(commandOutput = formatCommandOutput(result))
            }
            if (adbGateway.isConnectSuccess(result)) {
                val connectedDevice = buildConnectedDevice(trimmedEndpoint)
                _uiState.update { state ->
                    state.copy(
                        endpoint = trimmedEndpoint,
                        connectedDevices = listOf(connectedDevice),
                        currentSerial = connectedDevice.serial,
                    )
                }
                refreshDevicesInternal(
                    preferredSerial = trimmedEndpoint,
                    successStatus = "连接成功：$trimmedEndpoint",
                    failureStatus = "连接成功：$trimmedEndpoint，但设备列表刷新失败",
                    clearStaleDevicesOnFailure = false,
                    updateCommandOutput = false,
                )
                syncRemoteBrowserWithCurrentDevice()
            } else {
                _uiState.update { state ->
                    state.copy(statusText = result.stderr.ifBlank { "连接失败" })
                }
            }
            finishBusyAction()
        }
    }

    /**
     * 断开当前已连接的网络设备。
     *
     * @param serialInput 待断开的设备 serial；默认使用当前选中设备。
     */
    fun disconnectDevice(serialInput: String? = uiState.value.currentSerial) {
        val serial = serialInput ?: return
        val targetDevice = uiState.value.connectedDevices.firstOrNull { it.serial == serial }
        if (targetDevice?.connectionType != DeviceConnectionType.Network) {
            _uiState.update { state ->
                state.copy(statusText = "当前选中的是 USB 设备，不能断开")
            }
            return
        }

        _uiState.update { state ->
            state.copy(
                statusText = "正在断开设备...",
                busyAction = BusyAction.Disconnecting,
            )
        }

        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                adbGateway.disconnect(serial)
            }
            _uiState.update { state ->
                state.copy(commandOutput = formatCommandOutput(result))
            }
            if (result.exitCode == 0) {
                refreshDevicesInternal(
                    preferredSerial = null,
                    successStatus = "已断开：$serial",
                    updateCommandOutput = false,
                )
                syncRemoteBrowserWithCurrentDevice()
            } else {
                _uiState.update { state ->
                    state.copy(statusText = result.stderr.ifBlank { "断开失败" })
                }
            }
            finishBusyAction()
        }
    }

    /**
     * 刷新当前 adb 已连接设备列表。
     */
    fun refreshDevices() {
        _uiState.update { state ->
            state.copy(
                statusText = "正在刷新设备列表...",
                busyAction = BusyAction.Refreshing,
            )
        }

        viewModelScope.launch {
            refreshDevicesInternal(
                preferredSerial = uiState.value.currentSerial,
                updateCommandOutput = true,
            )
            syncRemoteBrowserWithCurrentDevice()
            finishBusyAction()
        }
    }

    /**
     * 刷新当前设备目录。
     */
    fun refreshRemoteDirectory() {
        val currentDirectory = uiState.value.fileBrowser.currentRemoteDirectory
        browseRemoteDirectory(
            targetDirectory = currentDirectory,
            successStatus = "已刷新目录：$currentDirectory",
        )
    }

    /**
     * 进入某个文件夹。
     *
     * @param entry 当前点击的远端条目；只有目录会触发跳转。
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
        val parentPath = parentRemotePath(uiState.value.fileBrowser.currentRemoteDirectory) ?: return
        browseRemoteDirectory(
            targetDirectory = parentPath,
            successStatus = "已返回目录：$parentPath",
        )
    }

    /**
     * 下载右键命中的文件或文件夹到指定本地目录。
     *
     * @param entry 当前选中的远端条目。
     * @param selectedDirectory UI 层选择好的本地目录；为空表示用户取消。
     */
    fun downloadRemoteEntryToDirectory(entry: RemoteFileEntry, selectedDirectory: String?) {
        val serial = uiState.value.currentDevice?.serial
        if (serial.isNullOrEmpty()) {
            _uiState.update { state ->
                state.copy(
                    statusText = "请先连接设备",
                ).withFileBrowser {
                    it.copy(statusText = "请先连接设备后再下载")
                }
            }
            return
        }

        if (selectedDirectory.isNullOrBlank()) {
            _uiState.update { state ->
                state.withFileBrowser {
                    it.copy(statusText = "已取消选择本地保存目录")
                }
            }
            return
        }

        _uiState.update { state ->
            state.copy(
                statusText = "正在下载：${entry.name}",
                busyAction = BusyAction.Pulling,
            ).withFileBrowser {
                it.copy(statusText = "正在下载到本地目录：$selectedDirectory")
            }.withTransfer {
                it.copy(
                    pullProgressPercent = null,
                    pullProgressLog = "正在准备下载：${entry.name}",
                )
            }
        }

        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                adbGateway.pull(
                    serial = serial,
                    remotePath = entry.path,
                    localPath = selectedDirectory,
                    onProgress = { progress ->
                        _uiState.update { state ->
                            state.withTransfer {
                                it.copy(
                                    pullProgressPercent = progress.percent ?: it.pullProgressPercent,
                                    pullProgressLog = appendTransferProgressLog(
                                        currentLog = it.pullProgressLog,
                                        latestLine = progress.latestLine,
                                    ),
                                )
                            }
                        }
                    },
                )
            }
            _uiState.update { state ->
                state.copy(commandOutput = formatCommandOutput(result))
            }
            if (result.exitCode == 0) {
                emitSnackbar(if (entry.type == RemoteFileType.Directory) "文件夹下载完成" else "文件下载完成")
                _uiState.update { state ->
                    state.copy(statusText = "已下载：${entry.name}").withFileBrowser {
                        it.copy(statusText = "已下载到：$selectedDirectory")
                    }
                }
                refreshRemoteDirectorySnapshot(serial)
            } else {
                val errorMessage = result.stderr.ifBlank { "下载失败" }
                _uiState.update { state ->
                    state.copy(statusText = errorMessage).withFileBrowser {
                        it.copy(statusText = errorMessage)
                    }
                }
            }
            finishBusyAction()
        }
    }

    /**
     * 向右键命中的位置上传用户已选中的本地文件。
     *
     * @param entry 当前右键命中的远端条目。
     * @param filePaths UI 层选择好的本地文件路径列表。
     */
    fun uploadSelectedLocalFiles(entry: RemoteFileEntry, filePaths: List<String>) {
        val serial = uiState.value.currentDevice?.serial
        if (serial.isNullOrEmpty()) {
            _uiState.update { state ->
                state.copy(statusText = "请先连接设备").withFileBrowser {
                    it.copy(statusText = "请先连接设备后再上传")
                }
            }
            return
        }

        val validationResult = validateSelectedLocalFiles(filePaths)
        if (validationResult.acceptedFilePaths.isEmpty()) {
            _uiState.update { state ->
                state.withFileBrowser {
                    it.copy(statusText = validationResult.statusText)
                }
            }
            return
        }

        val targetDirectory = resolveUploadTargetDirectory(entry)
        _uiState.update { state ->
            state.copy(
                statusText = "正在上传文件...",
                busyAction = BusyAction.Pushing,
            ).withFileBrowser {
                it.copy(statusText = "目标目录：$targetDirectory")
            }.withTransfer {
                it.copy(
                    pushCurrentFileName = null,
                    pushProgressPercent = null,
                    pushProgressLog = "正在准备上传到：$targetDirectory",
                )
            }
        }

        viewModelScope.launch {
            var lastResult: EmbeddedAdb.AdbCommandResult? = null
            var successCount = 0
            var failedFileName: String? = null

            for (filePath in validationResult.acceptedFilePaths) {
                val fileName = File(filePath).name
                _uiState.update { state ->
                    state.withTransfer {
                        it.copy(
                            pushCurrentFileName = fileName,
                            pushProgressPercent = null,
                            pushProgressLog = appendTransferProgressLog(
                                currentLog = it.pushProgressLog,
                                latestLine = "开始上传：$fileName",
                            ),
                        )
                    }
                }

                val result = withContext(ioDispatcher) {
                    adbGateway.push(
                        serial = serial,
                        localPath = filePath,
                        remotePath = targetDirectory,
                        onProgress = { progress ->
                            _uiState.update { state ->
                                state.withTransfer {
                                    it.copy(
                                        pushProgressPercent = progress.percent ?: it.pushProgressPercent,
                                        pushProgressLog = appendTransferProgressLog(
                                            currentLog = it.pushProgressLog,
                                            latestLine = progress.latestLine,
                                        ),
                                    )
                                }
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
                _uiState.update { state ->
                    state.copy(commandOutput = formatCommandOutput(finalResult))
                }
            }

            if (finalResult != null && finalResult.exitCode == 0) {
                emitSnackbar("文件上传完成")
                _uiState.update { state ->
                    state.copy(statusText = "成功上传 $successCount 个文件").withFileBrowser {
                        it.copy(statusText = "已上传到：$targetDirectory")
                    }
                }
                refreshRemoteDirectorySnapshot(serial)
            } else {
                val errorMessage = finalResult?.stderr
                    ?.ifBlank { null }
                    ?: "上传失败：${failedFileName ?: "未知文件"}"
                _uiState.update { state ->
                    state.copy(statusText = errorMessage).withFileBrowser {
                        it.copy(statusText = errorMessage)
                    }
                }
            }

            finishBusyAction()
        }
    }

    /**
     * 打开删除确认态。
     *
     * @param entry 当前等待删除确认的远端条目。
     */
    fun requestDeleteRemoteEntry(entry: RemoteFileEntry) {
        if (!uiState.value.isConnected) {
            _uiState.update { state ->
                state.copy(statusText = "请先连接设备")
            }
            return
        }
        _uiState.update { state ->
            state.withFileBrowser {
                it.copy(pendingDeleteEntry = entry)
            }
        }
    }

    /**
     * 关闭删除确认态。
     */
    fun dismissDeleteRemoteEntry() {
        _uiState.update { state ->
            state.withFileBrowser {
                it.copy(pendingDeleteEntry = null)
            }
        }
    }

    /**
     * 确认删除远端文件或目录。
     */
    fun confirmDeleteRemoteEntry() {
        val targetEntry = uiState.value.fileBrowser.pendingDeleteEntry ?: return
        val serial = uiState.value.currentDevice?.serial
        _uiState.update { state ->
            state.withFileBrowser {
                it.copy(pendingDeleteEntry = null)
            }
        }
        if (serial.isNullOrEmpty()) {
            _uiState.update { state ->
                state.copy(statusText = "请先连接设备").withFileBrowser {
                    it.copy(statusText = "请先连接设备后再删除")
                }
            }
            return
        }

        _uiState.update { state ->
            state.copy(
                statusText = "正在删除：${targetEntry.name}",
                busyAction = BusyAction.Deleting,
            ).withFileBrowser {
                it.copy(statusText = "正在删除：${targetEntry.path}")
            }
        }

        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                adbGateway.deleteRemoteEntry(
                    serial = serial,
                    remotePath = targetEntry.path,
                    isDirectory = targetEntry.type == RemoteFileType.Directory,
                )
            }
            _uiState.update { state ->
                state.copy(commandOutput = formatCommandOutput(result))
            }
            if (result.exitCode == 0) {
                emitSnackbar("已删除：${targetEntry.name}")
                _uiState.update { state ->
                    state.copy(statusText = "已删除：${targetEntry.name}")
                }
                refreshRemoteDirectorySnapshot(serial)
            } else {
                val errorMessage = result.stderr.ifBlank { "删除失败" }
                _uiState.update { state ->
                    state.copy(statusText = errorMessage).withFileBrowser {
                        it.copy(statusText = errorMessage)
                    }
                }
            }
            finishBusyAction()
        }
    }

    /**
     * 在当前选中的设备上安装单个 APK。
     *
     * 这里固定走覆盖安装，只处理当前需求，不额外扩散安装参数。
     */
    fun installApk() {
        val currentState = uiState.value
        val serial = currentState.currentDevice?.serial
        val trimmedApkPath = currentState.install.apkPath.trim()
        when {
            serial.isNullOrEmpty() -> {
                _uiState.update { state ->
                    state.withInstall {
                        it.copy(statusText = "请先连接设备后再安装 APK")
                    }
                }
                return
            }

            trimmedApkPath.isEmpty() -> {
                _uiState.update { state ->
                    state.withInstall {
                        it.copy(statusText = "请先选择 APK 文件")
                    }
                }
                return
            }
        }

        _uiState.update { state ->
            state.copy(busyAction = BusyAction.Installing).withInstall {
                it.copy(
                    statusText = "正在安装 APK...",
                    pendingDroppedApkPath = null,
                    isDropInstallConfirmVisible = false,
                )
            }
        }

        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                adbGateway.installApk(
                    serial = serial,
                    apkPath = trimmedApkPath,
                )
            }
            _uiState.update { state ->
                state.copy(commandOutput = formatCommandOutput(result)).withInstall {
                    it.copy(
                        statusText = if (result.exitCode == 0) {
                            "APK 安装成功"
                        } else {
                            result.stderr.ifBlank { "APK 安装失败" }
                        },
                    )
                }
            }
            if (result.exitCode == 0) {
                emitSnackbar("APK 安装完成")
            }
            finishBusyAction()
        }
    }

    /**
     * 执行一次设备列表刷新，并按优先顺序恢复当前选择。
     *
     * @param preferredSerial 优先尝试恢复选中的设备 serial。
     * @param successStatus 成功时要覆盖顶部状态的文案；为空时按默认规则生成。
     * @param failureStatus 失败时要覆盖顶部状态的文案；为空时使用 adb 错误输出。
     * @param clearStaleDevicesOnFailure 失败时是否清空旧设备列表。
     * @param updateCommandOutput 是否把本次命令输出写到界面底部。
     */
    private suspend fun refreshDevicesInternal(
        preferredSerial: String?,
        successStatus: String? = null,
        failureStatus: String? = null,
        clearStaleDevicesOnFailure: Boolean = true,
        updateCommandOutput: Boolean,
    ) {
        val deviceListResult = withContext(ioDispatcher) {
            adbGateway.listDevices()
        }
        _uiState.update { state ->
            val stateWithOutput = if (updateCommandOutput) {
                state.copy(commandOutput = formatCommandOutput(deviceListResult.commandResult))
            } else {
                state
            }
            if (deviceListResult.commandResult.exitCode != 0) {
                val clearedState = if (clearStaleDevicesOnFailure) {
                    stateWithOutput.copy(
                        connectedDevices = emptyList(),
                        currentSerial = null,
                    ).clearRemoteBrowser("设备列表刷新失败，暂时无法浏览文件")
                } else {
                    stateWithOutput
                }
                return@update clearedState.copy(
                    statusText = failureStatus ?: deviceListResult.commandResult.stderr.ifBlank { "刷新设备失败" },
                )
            }

            val devices = deviceListResult.devices
            val nextSelectedDevice = resolveNextSelectedDevice(
                devices = devices,
                preferredSerial = preferredSerial,
                currentSerial = stateWithOutput.currentSerial,
            )
            stateWithOutput.copy(
                connectedDevices = devices,
                currentSerial = nextSelectedDevice?.serial,
                statusText = when {
                    successStatus != null -> successStatus
                    devices.isEmpty() -> "未检测到已连接设备"
                    else -> "已刷新设备列表，共 ${devices.size} 台"
                },
            )
        }
    }

    /**
     * 统一进入设备目录浏览流程。
     *
     * @param targetDirectory 目标目录。
     * @param successStatus 读取成功后顶部状态的展示文案。
     */
    private fun browseRemoteDirectory(targetDirectory: String, successStatus: String) {
        val serial = uiState.value.currentDevice?.serial
        if (serial.isNullOrEmpty()) {
            _uiState.update { state ->
                state.copy(statusText = "请先连接设备").withFileBrowser {
                    it.copy(statusText = "请先连接设备后再浏览 $remoteBrowserRootPath")
                }
            }
            return
        }

        val normalizedTargetDirectory = normalizeRemotePath(targetDirectory)
        _uiState.update { state ->
            state.copy(
                statusText = "正在读取目录：$normalizedTargetDirectory",
                busyAction = BusyAction.BrowsingFiles,
            ).withFileBrowser {
                it.copy(statusText = "正在加载：$normalizedTargetDirectory")
            }
        }

        viewModelScope.launch {
            refreshRemoteDirectoryInternal(
                serial = serial,
                directoryPath = normalizedTargetDirectory,
                updateCommandOutput = true,
                successStatus = successStatus,
                clearEntriesOnFailure = false,
                showFailureInStatus = true,
            )
            finishBusyAction()
        }
    }

    /**
     * 在设备切换、连接成功或设备列表刷新后同步浏览器内容。
     *
     * 这里不覆盖顶部状态文案，让设备相关动作继续保留自己的结果提示。
     */
    private suspend fun syncRemoteBrowserWithCurrentDevice() {
        val currentDevice = uiState.value.currentDevice
        if (currentDevice == null) {
            _uiState.update { state ->
                state.clearRemoteBrowser("请先连接设备后再浏览 $remoteBrowserRootPath")
            }
            return
        }
        refreshRemoteDirectoryInternal(
            serial = currentDevice.serial,
            directoryPath = uiState.value.fileBrowser.currentRemoteDirectory,
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
     *
     * @param serial 当前目标设备 serial。
     */
    private suspend fun refreshRemoteDirectorySnapshot(serial: String) {
        refreshRemoteDirectoryInternal(
            serial = serial,
            directoryPath = uiState.value.fileBrowser.currentRemoteDirectory,
            updateCommandOutput = false,
            successStatus = null,
            clearEntriesOnFailure = false,
            showFailureInStatus = false,
        )
    }

    /**
     * 真正执行远端目录刷新，并按调用场景决定是否更新状态栏。
     *
     * @param serial 当前目标设备 serial。
     * @param directoryPath 待读取目录。
     * @param updateCommandOutput 是否更新命令输出。
     * @param successStatus 成功时是否覆盖顶部状态。
     * @param clearEntriesOnFailure 失败时是否清空当前目录条目。
     * @param showFailureInStatus 失败时是否同步更新顶部状态。
     * @return 是否读取成功。
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
        val result = withContext(ioDispatcher) {
            adbGateway.listRemoteEntries(serial, normalizedDirectoryPath)
        }

        var isSuccess = true
        _uiState.update { state ->
            val stateWithOutput = if (updateCommandOutput) {
                state.copy(commandOutput = formatCommandOutput(result.commandResult))
            } else {
                state
            }
            if (result.commandResult.exitCode != 0) {
                isSuccess = false
                val errorMessage = result.commandResult.stderr.ifBlank { "读取目录失败" }
                val stateWithBrowser = stateWithOutput.withFileBrowser {
                    it.copy(
                        remoteEntries = if (clearEntriesOnFailure) emptyList() else it.remoteEntries,
                        statusText = errorMessage,
                    )
                }
                return@update if (showFailureInStatus) {
                    stateWithBrowser.copy(statusText = errorMessage)
                } else {
                    stateWithBrowser
                }
            }

            val stateWithBrowser = stateWithOutput.withFileBrowser {
                it.copy(
                    currentRemoteDirectory = normalizedDirectoryPath,
                    remoteEntries = result.entries,
                    statusText = buildRemoteBrowserStatusText(result.entries),
                )
            }
            if (successStatus != null) {
                stateWithBrowser.copy(statusText = successStatus)
            } else {
                stateWithBrowser
            }
        }
        return isSuccess
    }

    /**
     * 计算刷新设备列表后的新选择。
     *
     * @param devices 当前最新设备列表。
     * @param preferredSerial 本次操作优先保留的 serial。
     * @param currentSerial 刷新前当前选中的 serial。
     * @return 刷新后应继续选中的设备；没有设备时返回 null。
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
     * 结束当前 busy 状态。
     *
     * 每个异步动作结束时统一调用，避免遗漏清理。
     */
    private fun finishBusyAction() {
        _uiState.update { state ->
            state.copy(busyAction = null)
        }
    }

    /**
     * 发送 Snackbar 效果。
     *
     * 使用挂起式 emit 发送，避免 Snackbar 展示期间后续提示因为缓冲区满而被 tryEmit 静默丢弃。
     *
     * @param message 要展示给用户的瞬时提示文案。
     */
    private fun emitSnackbar(message: String) {
        viewModelScope.launch {
            _effects.emit(AppUiEffect.ShowSnackbar(message))
        }
    }
}

/**
 * 把安装区域做局部 copy，避免主状态 copy 嵌套过深。
 */
private inline fun AppUiState.withInstall(transform: (InstallUiState) -> InstallUiState): AppUiState {
    return copy(install = transform(install))
}

/**
 * 把文件浏览器区域做局部 copy，避免主状态 copy 嵌套过深。
 */
private inline fun AppUiState.withFileBrowser(
    transform: (FileBrowserUiState) -> FileBrowserUiState,
): AppUiState {
    return copy(fileBrowser = transform(fileBrowser))
}

/**
 * 把上传 / 下载区域做局部 copy，避免主状态 copy 嵌套过深。
 */
private inline fun AppUiState.withTransfer(transform: (TransferUiState) -> TransferUiState): AppUiState {
    return copy(transfer = transform(transfer))
}

/**
 * 清空文件浏览器内容。
 *
 * @param statusText 清空后展示的状态文案。
 * @return 已重置文件浏览器区域的状态快照。
 */
private fun AppUiState.clearRemoteBrowser(statusText: String): AppUiState {
    return withFileBrowser {
        it.copy(
            currentRemoteDirectory = remoteBrowserRootPath,
            remoteEntries = emptyList(),
            statusText = statusText,
            pendingDeleteEntry = null,
        )
    }
}
