package com.location.adbtools

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

/**
 * 判断当前拖拽事件是否携带本地文件列表。
 *
 * Compose Desktop 的外部拖拽事件底层仍然暴露 AWT `Transferable`，
 * 这里集中做一次平台桥接，避免把类型判断散落到界面代码里。
 */
@OptIn(ExperimentalComposeUiApi::class)
fun canAcceptDroppedFiles(event: DragAndDropEvent): Boolean {
    return event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
}

/**
 * 从 Compose Desktop 的拖拽事件里提取本地文件绝对路径列表。
 *
 * @param event Compose Desktop 的外部拖拽事件。
 * @return 是文件拖拽时返回路径列表，否则返回 null。
 */
@OptIn(ExperimentalComposeUiApi::class)
fun extractDroppedFilePaths(event: DragAndDropEvent): List<String>? {
    return extractDroppedFilePaths(event.awtTransferable)
}

/**
 * 从 AWT `Transferable` 中提取本地文件绝对路径列表。
 *
 * @param transferable 外部拖拽携带的数据对象。
 * @return 是文件拖拽时返回路径列表，否则返回 null。
 */
fun extractDroppedFilePaths(transferable: Transferable): List<String>? {
    if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        return null
    }

    val rawFiles = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*> ?: return null
    return rawFiles.mapNotNull { item ->
        when (item) {
            is java.io.File -> item.absolutePath
            else -> null
        }
    }
}
