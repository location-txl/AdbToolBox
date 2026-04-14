package com.location.adbtools.device

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.location.adbtools.ui.InfoRow
import com.location.adbtools.ui.SectionTitle

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
        Button(onClick = onConnect, enabled = !isBusy) {
            Text("连接设备")
        }
        Button(onClick = onRefreshDevices, enabled = !isBusy) {
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
    val pointerModifier = if (isEnabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(pointerModifier)
            .hoverable(interactionSource = interactionSource, enabled = isEnabled)
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
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else secondaryTextColor,
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
