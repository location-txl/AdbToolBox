package com.location.adbtools

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 设备连接区域。
 *
 * 这里只负责展示输入框和连接/断开按钮，
 * 不直接处理 adb 命令和异步状态切换。
 */
@Composable
fun ConnectionSection(
    endpoint: String,
    isBusy: Boolean,
    connectedDevices: List<ConnectedDevice>,
    currentSerial: String?,
    onEndpointChange: (String) -> Unit,
    onConnect: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onRefreshDevices: () -> Unit,
    onDisconnectDevice: (String) -> Unit,
) {
    var isDropdownExpanded by remember { mutableStateOf(false) }
    val selectedDeviceLabel = connectedDevices
        .firstOrNull { it.serial == currentSerial }
        ?.displayName
        ?: "未选择设备"

    SectionTitle("连接设备")
    OutlinedTextField(
        value = endpoint,
        onValueChange = onEndpointChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("设备地址") },
        placeholder = { Text("例如 192.168.1.10:5555") },
        enabled = !isBusy,
        singleLine = true,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onConnect,
            enabled = !isBusy,
        ) {
            Text("连接设备")
        }
        Button(
            onClick = onRefreshDevices,
            enabled = !isBusy,
        ) {
            Text("刷新设备")
        }
    }
    Text(
        text = "已连接设备",
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        text = "点击下方选择当前要操作的设备",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(modifier = Modifier.fillMaxWidth()) {
        DeviceSelectorTrigger(
            selectedDeviceLabel = selectedDeviceLabel,
            isExpanded = isDropdownExpanded,
            isEnabled = connectedDevices.isNotEmpty() && !isBusy,
            onClick = { isDropdownExpanded = true },
        )
        DropdownMenu(
            expanded = isDropdownExpanded,
            onDismissRequest = { isDropdownExpanded = false },
        ) {
            connectedDevices.forEach { device ->
                DropdownMenuItem(
                    text = { Text(device.displayName) },
                    trailingIcon = {
                        if (device.connectionType == DeviceConnectionType.Network) {
                            TextButton(
                                onClick = {
                                    onDisconnectDevice(device.serial)
                                    isDropdownExpanded = false
                                },
                                enabled = !isBusy,
                            ) {
                                Text("断开连接")
                            }
                        }
                    },
                    onClick = {
                        onSelectDevice(device.serial)
                        isDropdownExpanded = false
                    },
                )
            }
        }
    }
}

/**
 * 当前设备选择入口。
 *
 * 这里故意做成“选择器”而不是高强调按钮，避免用户把它误读为静态状态条。
 * 通过描边、箭头、辅助文案和桌面端 hover 反馈，让可点击性更直观。
 *
 * @param selectedDeviceLabel 当前显示的设备名称；为空时应传入兜底文案。
 * @param isExpanded 下拉菜单是否已展开，用于同步箭头方向和高亮状态。
 * @param isEnabled 当前是否允许点击；禁用时只展示弱化态。
 * @param onClick 用户点击选择器后的回调；会触发展开菜单。
 */
@Composable
private fun DeviceSelectorTrigger(
    selectedDeviceLabel: String,
    isExpanded: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val containerColor = when {
        !isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        isExpanded -> MaterialTheme.colorScheme.secondaryContainer
        isHovered -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        !isEnabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        isExpanded -> MaterialTheme.colorScheme.primary
        isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val primaryTextColor = if (isEnabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }
    val secondaryTextColor = if (isEnabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    val pointerModifier = if (isEnabled) {
        Modifier.pointerHoverIcon(PointerIcon.Hand)
    } else {
        Modifier
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(pointerModifier)
            .hoverable(
                interactionSource = interactionSource,
                enabled = isEnabled,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled,
                onClick = onClick,
            ),
        color = containerColor,
        contentColor = primaryTextColor,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(width = 1.dp, color = borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = selectedDeviceLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor,
                )
                Text(
                    text = if (isEnabled) "点击切换当前设备" else "当前没有可选择的设备",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor,
                )
            }
            Column(
                modifier = Modifier.padding(start = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (isExpanded) "收起" else "选择设备",
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryTextColor,
                )
                Text(
                    text = if (isExpanded) "▲" else "▼",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        secondaryTextColor
                    },
                )
            }
        }
    }
}

/**
 * 当前设备状态展示区域。
 *
 * @param statusText 当前连接或执行状态文案。
 * @param currentDevice 当前选中设备；为空表示未连接。
 */
@Composable
fun CurrentStatusSection(statusText: String, currentDevice: ConnectedDevice?) {
    SectionTitle("当前状态")
    InfoRow("连接状态", statusText)
    InfoRow("当前设备", currentDevice?.displayName ?: "未连接")
    if (currentDevice != null) {
        InfoRow(
            "设备类型",
            if (currentDevice.connectionType == DeviceConnectionType.Network) "网络连接" else "USB 连接",
        )
    }
}

/**
 * APK 安装区域。
 *
 * 负责采集本地安装包路径并触发安装动作，始终针对当前选中的设备执行安装。
 */
@Composable
fun InstallApkSection(
    apkPath: String,
    isBusy: Boolean,
    isConnected: Boolean,
    installHint: String?,
    dragInstallHint: String,
    installStatusText: String,
    onSelectApkFile: () -> Unit,
    onInstallApk: () -> Unit,
) {
    val canInstallApk = !isBusy && isConnected && apkPath.isNotBlank()

    SectionTitle("安装 APK")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onSelectApkFile,
            enabled = !isBusy,
        ) {
            Text("选择 APK")
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (apkPath.isBlank()) "未选择 APK 文件" else apkPath,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = dragInstallHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Button(
        onClick = onInstallApk,
        enabled = canInstallApk,
    ) {
        Text("安装 APK")
    }
    Text(
        text = installStatusText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (installHint != null) {
        Text(
            text = installHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 文件浏览器区域。
 *
 * 主页只保留 `/sdcard` 根下的浏览能力，用户通过左键进目录、右键做下载/上传/删除。
 */
@Composable
fun FileBrowserSection(
    currentRemoteDirectory: String,
    remoteEntries: List<RemoteFileEntry>,
    fileBrowserStatusText: String,
    isBusy: Boolean,
    isConnected: Boolean,
    onNavigateUp: () -> Unit,
    onRefreshDirectory: () -> Unit,
    onOpenEntry: (RemoteFileEntry) -> Unit,
    onDownloadEntry: (RemoteFileEntry) -> Unit,
    onUploadToEntry: (RemoteFileEntry) -> Unit,
    onRequestDeleteEntry: (RemoteFileEntry) -> Unit,
) {
    val canNavigateUp = parentRemotePath(currentRemoteDirectory) != null

    SectionTitle("文件浏览器")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onNavigateUp,
            enabled = !isBusy && isConnected && canNavigateUp,
        ) {
            Text("返回上一级")
        }
        Button(
            onClick = onRefreshDirectory,
            enabled = !isBusy && isConnected,
        ) {
            Text("刷新目录")
        }
        Text(
            text = currentRemoteDirectory,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Text(
        text = fileBrowserStatusText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp, max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            when {
                !isConnected -> {
                    EmptyFileBrowserState("请先连接设备后再浏览 $remoteBrowserRootPath")
                }

                remoteEntries.isEmpty() -> {
                    EmptyFileBrowserState("当前目录为空")
                }

                else -> {
                    remoteEntries.forEach { entry ->
                        RemoteEntryRow(
                            entry = entry,
                            isBusy = isBusy,
                            onOpenEntry = onOpenEntry,
                            onDownloadEntry = onDownloadEntry,
                            onUploadToEntry = onUploadToEntry,
                            onRequestDeleteEntry = onRequestDeleteEntry,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 文件浏览器空态。
 */
@Composable
private fun EmptyFileBrowserState(message: String) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 文件浏览器中的单行条目。
 *
 * 目录支持左键进入；文件和目录都支持右键菜单。
 */
@Composable
private fun RemoteEntryRow(
    entry: RemoteFileEntry,
    isBusy: Boolean,
    onOpenEntry: (RemoteFileEntry) -> Unit,
    onDownloadEntry: (RemoteFileEntry) -> Unit,
    onUploadToEntry: (RemoteFileEntry) -> Unit,
    onRequestDeleteEntry: (RemoteFileEntry) -> Unit,
) {
    val isDirectory = entry.type == RemoteFileType.Directory
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val pointerModifier = if (isDirectory && !isBusy) {
        Modifier.pointerHoverIcon(PointerIcon.Hand)
    } else {
        Modifier
    }
    val rowColor = when {
        isHovered -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val typeText = if (isDirectory) "目录" else "文件"
    val tipText = when {
        isDirectory -> "左键进入，右键操作"
        else -> "右键操作"
    }

    ContextMenuArea(
        items = {
            buildList {
                if (isDirectory) {
                    add(ContextMenuItem("打开文件夹") { onOpenEntry(entry) })
                }
                add(ContextMenuItem("下载") { onDownloadEntry(entry) })
                add(ContextMenuItem("上传文件") { onUploadToEntry(entry) })
                add(ContextMenuItem("删除") { onRequestDeleteEntry(entry) })
            }
        },
        enabled = !isBusy,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(pointerModifier)
                .hoverable(
                    interactionSource = interactionSource,
                    enabled = true,
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = isDirectory && !isBusy,
                    onClick = { onOpenEntry(entry) },
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = rowColor,
            shape = MaterialTheme.shapes.small,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = buildString {
                            append(typeText)
                            if (entry.isHidden) {
                                append(" · 隐藏项")
                            }
                            append(" · ")
                            append(tipText)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (isDirectory) ">" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 命令输出展示区域。
 *
 * @param commandOutput 最近一次 adb 命令的格式化输出文本。
 */
@Composable
fun CommandOutputSection(commandOutput: String) {
    SectionTitle("命令输出")
    SelectionContainer {
        Text(
            text = commandOutput,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
