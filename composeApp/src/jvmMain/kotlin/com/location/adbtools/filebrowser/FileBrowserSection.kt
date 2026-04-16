package com.location.adbtools.filebrowser

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.location.adbtools.ui.SectionTitle

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
                !isConnected -> EmptyFileBrowserState("请先连接设备后再浏览 $remoteBrowserRootPath")
                remoteEntries.isEmpty() -> EmptyFileBrowserState("当前目录为空")
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
    val pointerModifier = if (isDirectory && !isBusy) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier
    val rowColor = if (isHovered) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    val typeText = if (isDirectory) "目录" else "文件"
    val tipText = if (isDirectory) "左键进入，右键操作" else "右键操作"

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
                .hoverable(interactionSource = interactionSource, enabled = true)
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
