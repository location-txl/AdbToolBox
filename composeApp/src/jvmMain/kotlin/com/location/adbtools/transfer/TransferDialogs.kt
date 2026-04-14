package com.location.adbtools.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
