package com.location.adbtools.install

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
import androidx.compose.material3.CircularProgressIndicator
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
import com.location.adbtools.device.ConnectedDevice
import java.io.File

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
                    CircularProgressIndicator()
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
