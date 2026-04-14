package com.location.adbtools.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.location.adbtools.ui.SectionTitle

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
