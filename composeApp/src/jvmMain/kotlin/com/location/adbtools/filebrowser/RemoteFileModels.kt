package com.location.adbtools.filebrowser

/**
 * 设备文件浏览器固定使用的根目录。
 *
 * 这次只围绕 `/sdcard` 做稳定浏览，不把范围扩到更多系统目录，
 * 这样路径规则和用户心智都更直接。
 */
const val remoteBrowserRootPath: String = "/sdcard"

/**
 * 远端条目的类型。
 */
enum class RemoteFileType {
    Directory,
    File,
}

/**
 * 设备文件浏览器中的单个远端条目。
 *
 * @property name 当前目录下展示给用户的文件名或目录名，不包含父路径。
 * @property path 该条目的绝对设备路径，始终位于 `/sdcard` 根目录内。
 * @property type 当前条目是目录还是普通文件。
 * @property isHidden 是否为隐藏项；这里用名称是否以 `.` 开头判断。
 */
data class RemoteFileEntry(
    val name: String,
    val path: String,
    val type: RemoteFileType,
    val isHidden: Boolean,
)

/**
 * 规范化设备路径，避免目录跳转时把多余斜杠和结尾斜杠继续传递下去。
 *
 * @param path 候选设备路径；为空时会回退到 `/sdcard`。
 * @return 去除多余斜杠后的稳定绝对路径。
 */
fun normalizeRemotePath(path: String): String {
    val trimmedPath = path.trim()
    if (trimmedPath.isEmpty()) {
        return remoteBrowserRootPath
    }

    val normalized = trimmedPath.replace(Regex("/+"), "/")
    return when {
        normalized == "/" -> remoteBrowserRootPath
        normalized == remoteBrowserRootPath -> remoteBrowserRootPath
        normalized.endsWith('/') -> normalized.dropLast(1)
        else -> normalized
    }
}

/**
 * 组合目录与子项名称，生成稳定的设备绝对路径。
 *
 * @param parentPath 父目录绝对路径。
 * @param childName 子项名称；这里只接受单层名称，不接受包含 `/` 的路径片段。
 * @return 拼好的设备绝对路径。
 */
fun joinRemotePath(parentPath: String, childName: String): String {
    val normalizedParentPath = normalizeRemotePath(parentPath)
    return if (normalizedParentPath == "/") {
        "/$childName"
    } else {
        "$normalizedParentPath/$childName"
    }
}

/**
 * 计算当前目录的父目录。
 *
 * @param path 当前设备绝对路径。
 * @return 父目录；如果已经是 `/sdcard` 根目录，则返回 null。
 */
fun parentRemotePath(path: String): String? {
    val normalizedPath = normalizeRemotePath(path)
    if (normalizedPath == remoteBrowserRootPath) {
        return null
    }

    val lastSeparatorIndex = normalizedPath.lastIndexOf('/')
    if (lastSeparatorIndex <= 0) {
        return remoteBrowserRootPath
    }

    val parentPath = normalizedPath.substring(0, lastSeparatorIndex)
    return if (parentPath.length < remoteBrowserRootPath.length) {
        remoteBrowserRootPath
    } else {
        parentPath
    }
}

/**
 * 计算右键“上传文件”时的目标目录。
 *
 * 文件夹直接作为上传目标；普通文件则上传到它所在目录。
 *
 * @param entry 当前右键命中的条目。
 * @return 实际用于 `adb push` 的设备目录。
 */
fun resolveUploadTargetDirectory(entry: RemoteFileEntry): String {
    return when (entry.type) {
        RemoteFileType.Directory -> entry.path
        RemoteFileType.File -> parentRemotePath(entry.path) ?: remoteBrowserRootPath
    }
}

/**
 * 对远端条目做稳定排序。
 *
 * 目录优先，文件次之；同类型内按名称忽略大小写排序，
 * 让隐藏项也能自然混在对应类型里，不再额外拆组。
 *
 * @param entries 待排序的远端条目列表。
 * @return 排序后的新列表。
 */
fun sortRemoteEntries(entries: List<RemoteFileEntry>): List<RemoteFileEntry> {
    return entries.sortedWith(
        compareBy<RemoteFileEntry>(
            { it.type != RemoteFileType.Directory },
            { it.name.lowercase() },
            { it.name },
        ),
    )
}
