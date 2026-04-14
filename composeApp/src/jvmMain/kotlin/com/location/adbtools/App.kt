package com.location.adbtools

import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.koinConfiguration
import java.awt.Window
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Adb Tools 桌面主界面入口。
 *
 * 顶层职责只保留三件事：启动 Koin、挂载 Navigation 3、装配当前窗口。
 */
@Composable
fun App(parentWindow: Window?) {
    KoinApplication(
        configuration = koinConfiguration {
            modules(appModule)
        },
    ) {
        MaterialTheme {
            AdbToolsNavigation(parentWindow = parentWindow)
        }
    }
}

/**
 * 应用导航根节点。
 *
 * 当前只有一个主页面，但先统一走 Navigation 3，
 * 这样后续扩页时不需要再推翻入口。
 */
@Composable
private fun AdbToolsNavigation(parentWindow: Window?) {
    val rootViewModelStoreOwner = rememberRootViewModelStoreOwner()
    val backStack = rememberNavBackStack(
        rememberNavSavedStateConfiguration(),
        AdbToolsHomeRoute,
    )

    CompositionLocalProvider(LocalViewModelStoreOwner provides rootViewModelStoreOwner) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(rootViewModelStoreOwner),
            ),
            entryProvider = entryProvider {
                entry<AdbToolsHomeRoute> {
                    val viewModel = koinViewModel<AdbToolsViewModel>()
                    AdbToolsHomeScreen(
                        viewModel = viewModel,
                        parentWindow = parentWindow,
                    )
                }
            },
        )
    }
}

/**
 * 为 Navigation 3 提供可序列化的 NavKey 配置。
 *
 * Navigation 3 在桌面端会把返回栈写进 SavedState，
 * 因此必须显式声明当前应用会用到的 NavKey 子类型。
 */
@Composable
private fun rememberNavSavedStateConfiguration(): SavedStateConfiguration {
    return remember {
        SavedStateConfiguration(SavedStateConfiguration.DEFAULT) {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(AdbToolsHomeRoute::class, AdbToolsHomeRoute.serializer())
                }
            }
        }
    }
}

/**
 * 为桌面窗口提供根级 ViewModelStoreOwner。
 *
 * Compose Desktop 没有 Activity/Fragment 宿主，
 * 所以这里手动提供一个窗口级 owner，供 Koin 与 Navigation 3 共享。
 */
@Composable
private fun rememberRootViewModelStoreOwner(): ViewModelStoreOwner {
    val viewModelStore = remember { ViewModelStore() }

    DisposableEffect(viewModelStore) {
        onDispose {
            viewModelStore.clear()
        }
    }

    return remember(viewModelStore) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = viewModelStore
        }
    }
}

/**
 * ADB 工具主页面。
 *
 * 这里继续负责页面装配和桌面文件选择，
 * 业务流程和异步 adb 调用下沉到 [AdbToolsViewModel]。
 */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun AdbToolsHomeScreen(viewModel: AdbToolsViewModel, parentWindow: Window?) {
    val uiState = viewModel.uiState
    val snackbarHostState = remember { SnackbarHostState() }
    val dropTarget = remember(viewModel) {
        object : DragAndDropTarget {
            override fun onDrop(event: androidx.compose.ui.draganddrop.DragAndDropEvent): Boolean {
                val droppedFiles = extractDroppedFilePaths(event) ?: return false
                viewModel.handleDroppedApkFiles(droppedFiles)
                return true
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.refreshDevices()
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.consumeSnackbarMessage() ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
    }

    val installHint = when {
        uiState.isConnected && uiState.apkPath.isBlank() -> "请选择或拖入 APK 文件"
        uiState.pendingDroppedApkPath != null -> "已接收拖入的 APK，确认后开始安装"
        else -> null
    }
    val dragInstallHint = "可直接拖拽 APK 到窗口安装"

    Surface(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .safeContentPadding()
            .fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .dragAndDropTarget(
                    shouldStartDragAndDrop = ::canAcceptDroppedFiles,
                    target = dropTarget,
                ),
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "ADB Tools",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "连接设备后，可以直接通过内置 adb 安装 APK 或浏览 /sdcard 文件。",
                    style = MaterialTheme.typography.bodyMedium,
                )

                ConnectionSection(
                    endpoint = uiState.endpoint,
                    isBusy = uiState.isBusy,
                    connectedDevices = uiState.connectedDevices,
                    currentSerial = uiState.currentSerial,
                    onEndpointChange = viewModel::updateEndpoint,
                    onConnect = viewModel::connectDevice,
                    onSelectDevice = viewModel::selectDevice,
                    onRefreshDevices = viewModel::refreshDevices,
                    onDisconnectDevice = viewModel::disconnectDevice,
                )
                CurrentStatusSection(
                    statusText = uiState.statusText,
                    currentDevice = uiState.currentDevice,
                )
                InstallApkSection(
                    apkPath = uiState.apkPath,
                    isBusy = uiState.isBusy,
                    isConnected = uiState.isConnected,
                    installHint = installHint,
                    dragInstallHint = dragInstallHint,
                    installStatusText = uiState.installStatusText,
                    onSelectApkFile = {
                        selectApkFile(parentWindow)?.let(viewModel::updateSelectedApkPath)
                    },
                    onInstallApk = viewModel::installApk,
                )
                FileBrowserSection(
                    currentRemoteDirectory = uiState.currentRemoteDirectory,
                    remoteEntries = uiState.remoteEntries,
                    fileBrowserStatusText = uiState.fileBrowserStatusText,
                    isBusy = uiState.isBusy,
                    isConnected = uiState.isConnected,
                    onNavigateUp = viewModel::navigateToParentRemoteDirectory,
                    onRefreshDirectory = viewModel::refreshRemoteDirectory,
                    onOpenEntry = viewModel::openRemoteEntry,
                    onDownloadEntry = { entry ->
                        viewModel.downloadRemoteEntryToDirectory(
                            entry = entry,
                            selectedDirectory = selectDirectory(parentWindow),
                        )
                    },
                    onUploadToEntry = { entry ->
                        viewModel.uploadSelectedLocalFiles(
                            entry = entry,
                            filePaths = selectLocalFiles(parentWindow),
                        )
                    },
                    onRequestDeleteEntry = viewModel::requestDeleteRemoteEntry,
                )
                CommandOutputSection(commandOutput = uiState.commandOutput)
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
            )

            if (uiState.busyAction == BusyAction.Pulling) {
                PullLoadingDialog(
                    progressPercent = uiState.pullProgressPercent,
                    progressLog = uiState.pullProgressLog,
                )
            }
            if (uiState.busyAction == BusyAction.Installing) {
                InstallLoadingDialog(
                    apkPath = uiState.apkPath,
                    currentDevice = uiState.currentDevice,
                )
            }
            if (uiState.busyAction == BusyAction.Pushing) {
                PushLoadingDialog(
                    currentFileName = uiState.pushCurrentFileName,
                    progressPercent = uiState.pushProgressPercent,
                    progressLog = uiState.pushProgressLog,
                )
            }

            val droppedApkPath = uiState.pendingDroppedApkPath
            if (uiState.isDropInstallConfirmVisible && droppedApkPath != null) {
                DropInstallConfirmDialog(
                    apkPath = droppedApkPath,
                    currentDevice = uiState.currentDevice,
                    onConfirm = viewModel::confirmDroppedApkInstall,
                    onDismiss = viewModel::dismissDroppedApkInstall,
                )
            }

            val pendingDeleteEntry = uiState.pendingDeleteEntry
            if (pendingDeleteEntry != null) {
                DeleteRemoteEntryConfirmDialog(
                    entry = pendingDeleteEntry,
                    onConfirm = viewModel::confirmDeleteRemoteEntry,
                    onDismiss = viewModel::dismissDeleteRemoteEntry,
                )
            }
        }
    }
}
