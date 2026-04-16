package com.location.adbtools.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Dialog
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Window
import java.io.File
import java.io.FilenameFilter
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * 桌面端原生目录选择器。
 *
 * 这里按平台分支实现，而不是继续统一走 `JFileChooser`，
 * 因为这次需求的重点就是让 `macOS` 和 `Windows` 真正弹出系统原生目录框。
 */
object NativeDirectoryPicker {

    /** Windows 本地路径缓冲区大小。 */
    private const val windowsMaxPath = 260

    /** `SHBrowseForFolder` 只返回文件系统目录。 */
    private const val browseFlagReturnOnlyFsDirs = 0x0001

    /** 启用较新的原生目录选择界面。 */
    private const val browseFlagNewDialogStyle = 0x0040

    /** 允许用户在对话框中直接编辑路径。 */
    private const val browseFlagEditBox = 0x0010

    /** 当前运行时操作系统名称。 */
    private val osName: String = System.getProperty("os.name").lowercase()

    /**
     * 打开系统目录选择框。
     *
     * @param parentWindow 当前桌面主窗口；允许为空。传入后可避免弹框层级不对。
     * @return 目录绝对路径；用户取消或系统调用失败时返回 null。
     */
    fun pickDirectory(parentWindow: Window?): String? {
        return when {
            osName.contains("mac") -> pickMacDirectory(parentWindow)
            osName.contains("windows") -> pickWindowsDirectory(parentWindow)
            else -> pickFallbackDirectory(parentWindow)
        }
    }

    /**
     * `macOS` 目录选择。
     *
     * AWT 的 `FileDialog` 在 macOS 上会映射到系统原生对话框；
     * 这里临时打开目录模式，结束后立刻恢复系统属性，避免污染其他文件选择行为。
     */
    private fun pickMacDirectory(parentWindow: Window?): String? {
        val propertyKey = "apple.awt.fileDialogForDirectories"
        val previousValue = System.getProperty(propertyKey)
        return try {
            System.setProperty(propertyKey, "true")
            val dialog = createFileDialog(
                parentWindow = parentWindow,
                title = "选择保存文件夹",
                mode = FileDialog.LOAD,
            ).apply {
                title = "选择保存文件夹"
                isVisible = true
            }
            dialog.directory?.let { selectedDirectory ->
                dialog.file?.let { selectedFile ->
                    File(selectedDirectory, selectedFile).absolutePath
                }
            }
        } finally {
            if (previousValue == null) {
                System.clearProperty(propertyKey)
            } else {
                System.setProperty(propertyKey, previousValue)
            }
        }
    }

    /**
     * `Windows` 目录选择。
     *
     * 这里直接调用 Shell 的原生文件夹浏览框，避免继续走 Swing 伪装成“系统样式”。
     * 取消选择时系统会返回空指针；拿到 PIDL 后必须主动释放，否则会泄漏本地内存。
     */
    private fun pickWindowsDirectory(parentWindow: Window?): String? {
        val initializeResult = Ole32.INSTANCE.OleInitialize(Pointer.NULL).toInt()
        if (initializeResult < 0) {
            return null
        }
        val browseInfo = BrowseInfo().apply {
            hwndOwner = parentWindow?.let { HWND(Native.getComponentPointer(it)) }
            lpszTitle = "选择保存文件夹"
            ulFlags = browseFlagReturnOnlyFsDirs or browseFlagNewDialogStyle or browseFlagEditBox
        }
        val pidl = NativeShell32.INSTANCE.SHBrowseForFolder(browseInfo) ?: return null
        return try {
            val pathBuffer = CharArray(windowsMaxPath)
            if (!NativeShell32.INSTANCE.SHGetPathFromIDList(pidl, pathBuffer)) {
                return null
            }
            Native.toString(pathBuffer)?.takeIf { it.isNotBlank() }
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(pidl)
            if (initializeResult == 0 || initializeResult == 1) {
                Ole32.INSTANCE.OleUninitialize()
            }
        }
    }

    /**
     * 非目标平台的保底目录选择。
     *
     * 当前需求只要求 `macOS + Windows` 原生，因此其他平台保持清晰可用即可，
     * 不为“理论完整性”再引入额外系统桥接代码。
     */
    private fun pickFallbackDirectory(parentWindow: Window?): String? {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "选择保存文件夹"
            isAcceptAllFileFilterUsed = false
        }
        val result = chooser.showOpenDialog(parentWindow)
        if (result != JFileChooser.APPROVE_OPTION) {
            return null
        }
        return chooser.selectedFile?.absolutePath
    }

    /**
     * 打开系统文件选择框并限制为单个 APK 文件。
     *
     * 这里不把文件选择继续抽成通用基础设施，因为当前真实需求只有 APK 安装；
     * 直接提供清晰入口，后续如果再出现其他文件类型，再按实际复用情况收敛。
     *
     * @param parentWindow 当前桌面主窗口；允许为空。
     * @return 选择成功时返回 APK 绝对路径；取消或失败时返回 null。
     */
    fun pickApkFile(parentWindow: Window?): String? {
        return when {
            osName.contains("mac") || osName.contains("windows") -> pickNativeApkFile(parentWindow)
            else -> pickFallbackApkFile(parentWindow)
        }
    }

    /**
     * 使用 AWT `FileDialog` 打开原生文件选择框。
     *
     * 这里统一给 `macOS` 和 `Windows` 走单文件选择，借助文件名过滤把范围收敛到 `.apk`，
     * 避免为了一个单用途文件选择框继续引入新的平台桥接代码。
     */
    private fun pickNativeApkFile(parentWindow: Window?): String? {
        val dialog = createFileDialog(
            parentWindow = parentWindow,
            title = "选择 APK 文件",
            mode = FileDialog.LOAD,
        ).apply {
            filenameFilter = FilenameFilter { _, name ->
                name.lowercase().endsWith(".apk")
            }
            isVisible = true
        }
        val selectedDirectory = dialog.directory ?: return null
        val selectedFile = dialog.file ?: return null
        return File(selectedDirectory, selectedFile)
            .takeIf { it.extension.equals("apk", ignoreCase = true) }
            ?.absolutePath
    }

    /**
     * 非目标平台的 APK 文件选择。
     *
     * 这里继续保留最直接的 Swing 兜底方案，只要求能稳定选择单个 `.apk` 文件。
     */
    private fun pickFallbackApkFile(parentWindow: Window?): String? {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            dialogTitle = "选择 APK 文件"
            isMultiSelectionEnabled = false
            isAcceptAllFileFilterUsed = false
            fileFilter = FileNameExtensionFilter("APK 文件 (*.apk)", "apk")
        }
        val result = chooser.showOpenDialog(parentWindow)
        if (result != JFileChooser.APPROVE_OPTION) {
            return null
        }
        return chooser.selectedFile
            ?.takeIf { it.extension.equals("apk", ignoreCase = true) }
            ?.absolutePath
    }

    /**
     * 打开系统文件选择框并允许一次选择多个普通文件。
     *
     * 这里的职责只负责把系统选择结果拿回来，不在这一层决定业务校验；
     * 是否允许目录、是否接受空结果，统一由动作层继续处理，避免平台差异散落到页面逻辑里。
     *
     * @param parentWindow 当前桌面主窗口；允许为空。
     * @return 用户选择的绝对路径列表；取消时返回空列表。
     */
    fun pickFiles(parentWindow: Window?): List<String> {
        return when {
            osName.contains("mac") || osName.contains("windows") -> pickNativeFiles(parentWindow)
            else -> pickFallbackFiles(parentWindow)
        }
    }

    /**
     * 使用原生文件选择框执行多文件选择。
     *
     * AWT `FileDialog` 在目标平台上能直接映射系统文件选择器；
     * 这里打开多选模式，保持能力简单直接，不额外包装成更重的通用文件系统组件。
     */
    private fun pickNativeFiles(parentWindow: Window?): List<String> {
        val dialog = createFileDialog(
            parentWindow = parentWindow,
            title = "选择要推送的文件",
            mode = FileDialog.LOAD,
        ).apply {
            isMultipleMode = true
            isVisible = true
        }
        return dialog.files
            ?.map(File::getAbsolutePath)
            .orEmpty()
    }

    /**
     * 非目标平台的多文件选择兜底实现。
     *
     * 这里明确只允许选择普通文件，和 push 功能的产品边界保持一致。
     */
    private fun pickFallbackFiles(parentWindow: Window?): List<String> {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            dialogTitle = "选择要推送的文件"
            isMultiSelectionEnabled = true
            isAcceptAllFileFilterUsed = true
        }
        val result = chooser.showOpenDialog(parentWindow)
        if (result != JFileChooser.APPROVE_OPTION) {
            return emptyList()
        }
        return chooser.selectedFiles
            ?.map(File::getAbsolutePath)
            .orEmpty()
    }

    /**
     * 按父窗口类型创建 `FileDialog`。
     *
     * `FileDialog` 只接受 `Frame` 或 `Dialog` 作为父窗口；
     * 这里把分支收口，避免调用点重复判断 AWT 窗口类型。
     */
    private fun createFileDialog(parentWindow: Window?, title: String, mode: Int): FileDialog {
        return when (parentWindow) {
            is Frame -> FileDialog(parentWindow, title, mode)
            is Dialog -> FileDialog(parentWindow, title, mode)
            else -> FileDialog(null as Frame?, title, mode)
        }
    }

    /**
     * `SHBrowseForFolderW` 所需输入结构。
     *
     * 我们只填这次目录选择真正会用到的字段，不额外实现回调或过滤，
     * 避免为了一个简单文件夹选择器把 Windows Shell 代码写成基础设施。
     */
    @Structure.FieldOrder(
        "hwndOwner",
        "pidlRoot",
        "pszDisplayName",
        "lpszTitle",
        "ulFlags",
        "lpfn",
        "lParam",
        "iImage",
    )
    class BrowseInfo : Structure() {

        /** 对话框归属的父窗口句柄；为空时系统自行决定层级。 */
        @JvmField
        var hwndOwner: HWND? = null

        /** 浏览根节点；为空表示从整个 Shell 命名空间开始。 */
        @JvmField
        var pidlRoot: Pointer? = null

        /** 接收显示名称的缓冲区；这里不读取显示名，因此保持为空。 */
        @JvmField
        var pszDisplayName: Pointer? = null

        /** 弹框标题。 */
        @JvmField
        var lpszTitle: String? = null

        /** 控制原生目录框行为的标记位。 */
        @JvmField
        var ulFlags: Int = 0

        /** 当前不需要自定义回调，因此保持为空。 */
        @JvmField
        var lpfn: Pointer? = null

        /** 传给回调的自定义参数；无回调时保持为空。 */
        @JvmField
        var lParam: Pointer? = null

        /** 返回所选目录对应图标索引；当前不使用。 */
        @JvmField
        var iImage: Int = 0
    }

    /**
     * Windows Shell 目录选择的最小原生声明。
     *
     * JNA 平台包没有直接提供这两个目录选择 API 的现成 Kotlin 可用封装，
     * 这里补一层最薄声明，范围只限当前目录选择需求。
     */
    private interface NativeShell32 : StdCallLibrary {

        fun SHBrowseForFolder(lpbi: BrowseInfo): Pointer?

        fun SHGetPathFromIDList(pidl: Pointer, pszPath: CharArray): Boolean

        companion object {
            val INSTANCE: NativeShell32 = Native.load(
                "shell32",
                NativeShell32::class.java,
                W32APIOptions.UNICODE_OPTIONS,
            )
        }
    }
}
