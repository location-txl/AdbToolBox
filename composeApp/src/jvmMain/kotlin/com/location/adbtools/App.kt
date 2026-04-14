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
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.awt.Window

/**
 * Adb Tools 桌面主界面入口。
 *
 * 这里只负责页面装配：准备状态、动作和窗口级拖拽接线，
 * 具体业务动作和分区 UI 继续下沉到独立文件。
 */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun App(parentWindow: Window?) {
    MaterialTheme {
        val uiState = rememberAppUiState()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val actions = rememberAppActions(
            uiState = uiState,
            scope = scope,
            parentWindow = parentWindow,
        )
        val dropTarget = remember(actions) {
            object : DragAndDropTarget {
                override fun onDrop(event: androidx.compose.ui.draganddrop.DragAndDropEvent): Boolean {
                    val droppedFiles = extractDroppedFilePaths(event) ?: return false
                    actions.handleDroppedApkFiles(droppedFiles)
                    return true
                }
            }
        }

        LaunchedEffect(Unit) {
            actions.refreshDevices()
        }

        val snackbarMessage = uiState.snackbarMessage
        LaunchedEffect(snackbarMessage) {
            val message = snackbarMessage ?: return@LaunchedEffect
            snackbarHostState.showSnackbar(message)
            if (uiState.snackbarMessage == message) {
                uiState.snackbarMessage = null
            }
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
                        onEndpointChange = actions::updateEndpoint,
                        onConnect = actions::connectDevice,
                        onSelectDevice = actions::selectDevice,
                        onRefreshDevices = actions::refreshDevices,
                        onDisconnectDevice = actions::disconnectDevice,
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
                        onSelectApkFile = actions::selectApkFile,
                        onInstallApk = actions::installApk,
                    )
                    FileBrowserSection(
                        currentRemoteDirectory = uiState.currentRemoteDirectory,
                        remoteEntries = uiState.remoteEntries,
                        fileBrowserStatusText = uiState.fileBrowserStatusText,
                        isBusy = uiState.isBusy,
                        isConnected = uiState.isConnected,
                        onNavigateUp = actions::navigateToParentRemoteDirectory,
                        onRefreshDirectory = actions::refreshRemoteDirectory,
                        onOpenEntry = actions::openRemoteEntry,
                        onDownloadEntry = actions::downloadRemoteEntry,
                        onUploadToEntry = actions::uploadToRemoteEntry,
                        onRequestDeleteEntry = actions::requestDeleteRemoteEntry,
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
                        onConfirm = actions::confirmDroppedApkInstall,
                        onDismiss = actions::dismissDroppedApkInstall,
                    )
                }

                val pendingDeleteEntry = uiState.pendingDeleteEntry
                if (pendingDeleteEntry != null) {
                    DeleteRemoteEntryConfirmDialog(
                        entry = pendingDeleteEntry,
                        onConfirm = actions::confirmDeleteRemoteEntry,
                        onDismiss = actions::dismissDeleteRemoteEntry,
                    )
                }
            }
        }
    }
}
