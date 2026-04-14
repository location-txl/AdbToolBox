package com.location.adbtools

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import java.awt.Window
import java.io.File

/**
 * 拉取文件时的应用内遮罩层。
 *
 * 不再单独创建系统级窗口，避免弹层脱离应用窗口中心。
 */
@Composable
fun PullLoadingDialog(progressPercent: Int?, progressLog: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(min = 420.dp, max = 640.dp)
                .fillMaxWidth(0.8f),
            tonalElevation = 4.dp,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "正在下载文件或目录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (progressPercent != null) {
                    LinearProgressIndicator(
                        progress = { progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "当前进度：$progressPercent%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                    Text(
                        text = "正在等待 adb 返回进度...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                SelectionContainer {
                    Text(
                        text = progressLog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 280.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * 安装 APK 时的应用内遮罩层。
 *
 * 使用与拖拽确认层一致的应用内弹层方式，让安装过程始终附着在当前窗口内，
 * 避免用户误以为点击后没有响应或重复触发安装。
 */
@Composable
fun InstallLoadingDialog(apkPath: String, currentDevice: ConnectedDevice?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(min = 460.dp, max = 680.dp)
                .fillMaxWidth(0.78f),
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
                    text = "正在安装 APK",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
                Text(
                    text = "请等待 adb 完成安装，安装期间不要重复点击。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "目标设备：${currentDevice?.displayName ?: "未连接设备"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "安装文件：${File(apkPath).name}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SelectionContainer {
                    Text(
                        text = apkPath,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * 推送文件时的应用内遮罩层。
 *
 * 多文件 push 没必要再拆一套独立窗口；保持和 pull 一样附着在主界面上，
 * 用户更容易理解当前正在处理哪个文件，也不容易误触重复提交。
 */
@Composable
fun PushLoadingDialog(
    currentFileName: String?,
    progressPercent: Int?,
    progressLog: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(min = 420.dp, max = 640.dp)
                .fillMaxWidth(0.8f),
            tonalElevation = 4.dp,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "正在上传文件",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (progressPercent != null) {
                    LinearProgressIndicator(
                        progress = { progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "当前进度：$progressPercent%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                    Text(
                        text = "正在等待 adb 返回进度...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = "当前文件：${currentFileName ?: "准备中"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SelectionContainer {
                    Text(
                        text = progressLog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 280.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * 拖入 APK 后的确认弹层。
 *
 * 这里使用应用内居中遮罩，而不是独立 Dialog 窗口，
 * 这样弹层位置会跟随软件窗口，尺寸也更可控。
 */
@Composable
fun DropInstallConfirmDialog(
    apkPath: String,
    currentDevice: ConnectedDevice?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scrimInteractionSource = remember { MutableInteractionSource() }
    val panelInteractionSource = remember { MutableInteractionSource() }

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
                    text = "检测到拖入的 APK",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "文件：${File(apkPath).name}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "目标设备：${currentDevice?.displayName ?: "未连接设备"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SelectionContainer {
                    Text(
                        text = apkPath,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Button(onClick = onConfirm) {
                        Text("安装 APK")
                    }
                }
            }
        }
    }
}

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

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "$label：",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * 追加实时输出时做简单裁剪，避免长时间拉取把状态文本无限撑大。
 */
fun appendTransferProgressLog(currentLog: String, latestLine: String): String {
    val mergedLines = buildList {
        addAll(currentLog.lines().filter { it.isNotBlank() })
        add(latestLine)
    }
    return mergedLines.takeLast(12).joinToString(separator = "\n")
}

/**
 * 打开系统目录选择框，返回用户选中的目录绝对路径。
 */
fun selectDirectory(parentWindow: Window?): String? {
    return NativeDirectoryPicker.pickDirectory(parentWindow)
}

/**
 * 打开系统文件选择框，返回用户选中的 APK 绝对路径。
 */
fun selectApkFile(parentWindow: Window?): String? {
    return NativeDirectoryPicker.pickApkFile(parentWindow)
}

/**
 * 打开系统文件选择框，返回用户选择的多个本地文件绝对路径。
 */
fun selectLocalFiles(parentWindow: Window?): List<String> {
    return NativeDirectoryPicker.pickFiles(parentWindow)
}

/**
 * 当前界面的忙碌动作类型。
 */
enum class BusyAction {
    Connecting,
    Disconnecting,
    Refreshing,
    BrowsingFiles,
    Deleting,
    Installing,
    Pulling,
    Pushing,
}
