package com.location.adbtools.install

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.location.adbtools.ui.SectionTitle

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
        Button(onClick = onSelectApkFile, enabled = !isBusy) {
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
    Button(onClick = onInstallApk, enabled = canInstallApk) {
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
