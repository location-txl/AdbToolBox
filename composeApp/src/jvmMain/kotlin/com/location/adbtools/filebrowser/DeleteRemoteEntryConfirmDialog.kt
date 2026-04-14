package com.location.adbtools.filebrowser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 删除远端文件或目录前的确认弹层。
 *
 * 删除是不可逆操作，这里强制二次确认，
 * 避免桌面端右键菜单误触后直接把设备文件删掉。
 */
@Composable
fun DeleteRemoteEntryConfirmDialog(
    entry: RemoteFileEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scrimInteractionSource = remember { MutableInteractionSource() }
    val panelInteractionSource = remember { MutableInteractionSource() }
    val entryTypeText = if (entry.type == RemoteFileType.Directory) "文件夹" else "文件"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f))
            .clickable(
                interactionSource = scrimInteractionSource,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(min = 460.dp, max = 680.dp)
                .fillMaxWidth(0.78f)
                .clickable(
                    interactionSource = panelInteractionSource,
                    indication = null,
                    onClick = {},
                ),
            tonalElevation = 6.dp,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "确认删除$entryTypeText",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "名称：${entry.name}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "类型：$entryTypeText",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SelectionContainer {
                    Text(
                        text = entry.path,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = "删除后不可恢复，请确认当前目标无误。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Button(onClick = onConfirm) {
                        Text("删除")
                    }
                }
            }
        }
    }
}
