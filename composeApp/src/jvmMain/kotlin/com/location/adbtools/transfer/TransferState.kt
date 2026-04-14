package com.location.adbtools.transfer

import java.io.File

/**
 * 上传 / 下载相关的界面状态。
 *
 * @property pullProgressPercent 下载过程中解析出的进度百分比；未知时为 null。
 * @property pullProgressLog 下载过程中最近展示的实时日志。
 * @property pushCurrentFileName 上传过程中当前正在处理的文件名。
 * @property pushProgressPercent 上传过程中解析出的进度百分比；未知时为 null。
 * @property pushProgressLog 上传过程中最近展示的实时日志。
 */
data class TransferUiState(
    val pullProgressPercent: Int? = null,
    val pullProgressLog: String = "等待开始下载",
    val pushCurrentFileName: String? = null,
    val pushProgressPercent: Int? = null,
    val pushProgressLog: String = "等待开始上传",
)

/**
 * 本地上传文件选择结果。
 *
 * @property acceptedFilePaths 通过校验的本地文件绝对路径列表。
 * @property statusText 当前校验结果对应的提示文案。
 */
data class LocalFileSelectionResult(
    val acceptedFilePaths: List<String>,
    val statusText: String,
)

/**
 * 校验本地文件选择结果，只接受存在的普通文件。
 *
 * @param filePaths 用户选择的本地路径列表。
 * @return 经过过滤后的可上传文件列表和对应提示文案。
 */
fun validateSelectedLocalFiles(filePaths: List<String>): LocalFileSelectionResult {
    if (filePaths.isEmpty()) {
        return LocalFileSelectionResult(
            acceptedFilePaths = emptyList(),
            statusText = "未选择要上传的文件",
        )
    }

    val acceptedFiles = mutableListOf<File>()
    var ignoredDirectoryCount = 0
    var ignoredInvalidCount = 0
    filePaths.forEach { filePath ->
        val file = File(filePath)
        when {
            file.isDirectory -> ignoredDirectoryCount += 1
            file.exists() && file.isFile -> acceptedFiles += file
            else -> ignoredInvalidCount += 1
        }
    }

    if (acceptedFiles.isEmpty()) {
        val statusText = when {
            ignoredDirectoryCount > 0 -> "暂不支持上传文件夹"
            else -> "选择的文件不存在或不是普通文件"
        }
        return LocalFileSelectionResult(
            acceptedFilePaths = emptyList(),
            statusText = statusText,
        )
    }

    val baseStatus = if (acceptedFiles.size == 1) {
        "已选择 1 个文件：${acceptedFiles.first().name}"
    } else {
        "已选择 ${acceptedFiles.size} 个文件"
    }
    val ignoredStatus = buildList {
        if (ignoredDirectoryCount > 0) {
            add("已忽略 $ignoredDirectoryCount 个文件夹")
        }
        if (ignoredInvalidCount > 0) {
            add("已忽略 $ignoredInvalidCount 个无效路径")
        }
    }.joinToString(separator = "，")

    return LocalFileSelectionResult(
        acceptedFilePaths = acceptedFiles.map(File::getAbsolutePath),
        statusText = if (ignoredStatus.isBlank()) baseStatus else "$baseStatus，$ignoredStatus",
    )
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
