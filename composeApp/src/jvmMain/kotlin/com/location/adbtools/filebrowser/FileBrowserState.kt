package com.location.adbtools.filebrowser

/**
 * 文件浏览器区域的界面状态。
 *
 * @property currentRemoteDirectory 当前所在的设备目录。
 * @property remoteEntries 当前目录下的远端条目列表。
 * @property statusText 文件浏览器区域展示的状态文案。
 * @property pendingDeleteEntry 当前等待确认删除的远端条目。
 */
data class FileBrowserUiState(
    val currentRemoteDirectory: String = remoteBrowserRootPath,
    val remoteEntries: List<RemoteFileEntry> = emptyList(),
    val statusText: String = "请先连接设备后再浏览 $remoteBrowserRootPath",
    val pendingDeleteEntry: RemoteFileEntry? = null,
)

/**
 * 生成文件浏览器区域的摘要文案。
 *
 * @param entries 当前目录条目列表。
 * @return 适合直接展示在界面上的摘要文本。
 */
fun buildRemoteBrowserStatusText(entries: List<RemoteFileEntry>): String {
    if (entries.isEmpty()) {
        return "当前目录为空"
    }

    val directoryCount = entries.count { it.type == RemoteFileType.Directory }
    val fileCount = entries.count { it.type == RemoteFileType.File }
    val hiddenCount = entries.count { it.isHidden }
    return buildString {
        append("共 ${entries.size} 项")
        append("，文件夹 $directoryCount 个")
        append("，文件 $fileCount 个")
        if (hiddenCount > 0) {
            append("，隐藏项 $hiddenCount 个")
        }
    }
}
