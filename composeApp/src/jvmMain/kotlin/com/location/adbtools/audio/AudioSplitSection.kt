package com.location.adbtools.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.location.adbtools.ui.SectionTitle

/**
 * 音频 PCM 拆分区域。
 *
 * 这里只负责收集用户输入并展示结果，不直接参与文件 IO 或 PCM 拆分。
 */
@Composable
fun AudioSplitSection(
    inputPath: String,
    outputDirectory: String,
    statusText: String,
    resultText: String,
    isRunning: Boolean,
    onSelectInputPath: () -> Unit,
    onSelectOutputDirectory: () -> Unit,
    onSplitAudio: () -> Unit,
) {
    SectionTitle("音频拆分")
    Text(
        text = statusText,
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = inputPath,
        onValueChange = {},
        modifier = Modifier.fillMaxWidth(),
        label = { Text("输入路径") },
        placeholder = { Text("选择 PCM 文件或包含 PCM 的目录") },
        enabled = false,
        readOnly = true,
        singleLine = true,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onSelectInputPath,
            enabled = !isRunning,
        ) {
            Text("选择输入")
        }
        Button(
            onClick = onSelectOutputDirectory,
            enabled = !isRunning,
        ) {
            Text("选择输出目录")
        }
    }
    OutlinedTextField(
        value = outputDirectory,
        onValueChange = {},
        modifier = Modifier.fillMaxWidth(),
        label = { Text("输出目录") },
        placeholder = { Text("选择拆分结果输出目录") },
        enabled = false,
        readOnly = true,
        singleLine = true,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "固定参数：4 声道 / 16bit little-endian / 16kHz",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = onSplitAudio,
            enabled = !isRunning,
        ) {
            Text(if (isRunning) "拆分中..." else "开始拆分")
        }
    }
    SelectionContainer {
        Text(
            text = resultText,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
