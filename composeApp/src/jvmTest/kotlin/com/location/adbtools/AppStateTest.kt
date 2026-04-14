package com.location.adbtools

import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 覆盖主页面状态层中的关键校验和文件浏览器规则。
 */
class AppStateTest {

    @Test
    fun `validate dropped apk files should accept single apk`() {
        val apkPath = createTempDirectory("adbtools-apk").resolve("demo.apk")
        apkPath.writeText("fake apk")

        val result = validateDroppedApkFiles(listOf(apkPath.toString()))

        assertEquals(apkPath.toFile().absolutePath, result.acceptedApkPath)
        assertTrue(result.statusText.contains("demo.apk"))
    }

    @Test
    fun `validate dropped apk files should reject non apk`() {
        val textPath = createTempDirectory("adbtools-txt").resolve("demo.txt")
        textPath.writeText("not apk")

        val result = validateDroppedApkFiles(listOf(textPath.toString()))

        assertNull(result.acceptedApkPath)
        assertEquals("只支持拖入 .apk 文件", result.statusText)
    }

    @Test
    fun `validate dropped apk files should reject multi files`() {
        val tempDir = createTempDirectory("adbtools-multi")
        val firstApk = tempDir.resolve("one.apk")
        val secondApk = tempDir.resolve("two.apk")
        firstApk.writeText("1")
        secondApk.writeText("2")

        val result = validateDroppedApkFiles(listOf(firstApk.toString(), secondApk.toString()))

        assertNull(result.acceptedApkPath)
        assertEquals("一次只能拖入一个 APK 文件", result.statusText)
    }

    @Test
    fun `validate dropped apk files should reject missing path`() {
        val missingPath = createTempDirectory("adbtools-missing").resolve("lost.apk")

        val result = validateDroppedApkFiles(listOf(missingPath.toString()))

        assertNull(result.acceptedApkPath)
        assertEquals("拖入的文件不存在或不是普通文件", result.statusText)
    }

    @Test
    fun `handle dropped apk files should update pending confirmation state`() {
        val apkPath = createTempDirectory("adbtools-drop").resolve("drag.apk")
        apkPath.writeText("fake apk")
        val viewModel = AdbToolsViewModel()
        val uiState = viewModel.uiState

        viewModel.handleDroppedApkFiles(listOf(apkPath.toString()))

        val acceptedPath = uiState.pendingDroppedApkPath
        assertNotNull(acceptedPath)
        assertEquals(apkPath.toFile().absolutePath, acceptedPath)
        assertEquals(acceptedPath, uiState.apkPath)
        assertTrue(uiState.isDropInstallConfirmVisible)
    }

    @Test
    fun `confirm dropped apk install should reuse install flow validation`() {
        val apkPath = createTempDirectory("adbtools-confirm").resolve("drag.apk")
        apkPath.writeText("fake apk")
        val viewModel = AdbToolsViewModel()
        val uiState = viewModel.uiState.apply {
            pendingDroppedApkPath = apkPath.toFile().absolutePath
            isDropInstallConfirmVisible = true
        }

        viewModel.confirmDroppedApkInstall()

        assertEquals(apkPath.toFile().absolutePath, uiState.apkPath)
        assertEquals("请先连接设备后再安装 APK", uiState.installStatusText)
        assertNull(uiState.pendingDroppedApkPath)
        assertFalse(uiState.isDropInstallConfirmVisible)
    }

    @Test
    fun `dismiss dropped apk install should only close confirmation`() {
        val viewModel = AdbToolsViewModel()
        val uiState = viewModel.uiState.apply {
            apkPath = "C:/tmp/demo.apk"
            pendingDroppedApkPath = apkPath
            isDropInstallConfirmVisible = true
        }

        viewModel.dismissDroppedApkInstall()

        assertEquals("C:/tmp/demo.apk", uiState.apkPath)
        assertNull(uiState.pendingDroppedApkPath)
        assertFalse(uiState.isDropInstallConfirmVisible)
        assertEquals("已取消拖拽安装", uiState.installStatusText)
    }

    @Test
    fun `validate selected local files should keep normal files`() {
        val tempDir = createTempDirectory("adbtools-upload-select")
        val firstFile = tempDir.resolve("one.txt")
        val secondFile = tempDir.resolve("two.txt")
        firstFile.writeText("1")
        secondFile.writeText("2")

        val result = validateSelectedLocalFiles(listOf(firstFile.toString(), secondFile.toString()))

        assertEquals(2, result.acceptedFilePaths.size)
        assertTrue(result.statusText.contains("已选择 2 个文件"))
    }

    @Test
    fun `validate selected local files should reject folders`() {
        val tempDir = createTempDirectory("adbtools-upload-dir")

        val result = validateSelectedLocalFiles(listOf(tempDir.toString()))

        assertTrue(result.acceptedFilePaths.isEmpty())
        assertEquals("暂不支持上传文件夹", result.statusText)
    }

    @Test
    fun `validate selected local files should ignore folders and keep files`() {
        val tempDir = createTempDirectory("adbtools-upload-mixed")
        val filePath = tempDir.resolve("demo.txt")
        val childDirectory = tempDir.resolve("nested").toFile().apply { mkdirs() }
        filePath.writeText("demo")

        val result = validateSelectedLocalFiles(listOf(filePath.toString(), childDirectory.absolutePath))

        assertEquals(listOf(filePath.toFile().absolutePath), result.acceptedFilePaths)
        assertTrue(result.statusText.contains("已忽略 1 个文件夹"))
    }

    @Test
    fun `refresh remote directory should require connected device`() {
        val viewModel = AdbToolsViewModel()
        val uiState = viewModel.uiState

        viewModel.refreshRemoteDirectory()

        assertEquals("请先连接设备", uiState.statusText)
        assertEquals("请先连接设备后再浏览 /sdcard", uiState.fileBrowserStatusText)
        assertFalse(uiState.isBusy)
    }

    @Test
    fun `request delete remote entry should store pending entry when connected`() {
        val device = buildConnectedDevice("R58M123ABC")
        val targetEntry = RemoteFileEntry(
            name = "Download",
            path = "/sdcard/Download",
            type = RemoteFileType.Directory,
            isHidden = false,
        )
        val viewModel = AdbToolsViewModel()
        val uiState = viewModel.uiState.apply {
            connectedDevices = listOf(device)
            currentSerial = device.serial
        }

        viewModel.requestDeleteRemoteEntry(targetEntry)

        assertEquals(targetEntry, uiState.pendingDeleteEntry)
    }

    @Test
    fun `resolve upload target directory should use folder path for directory`() {
        val directoryEntry = RemoteFileEntry(
            name = "Pictures",
            path = "/sdcard/Pictures",
            type = RemoteFileType.Directory,
            isHidden = false,
        )

        val result = resolveUploadTargetDirectory(directoryEntry)

        assertEquals("/sdcard/Pictures", result)
    }

    @Test
    fun `resolve upload target directory should use parent path for file`() {
        val fileEntry = RemoteFileEntry(
            name = "demo.txt",
            path = "/sdcard/Download/demo.txt",
            type = RemoteFileType.File,
            isHidden = false,
        )

        val result = resolveUploadTargetDirectory(fileEntry)

        assertEquals("/sdcard/Download", result)
    }

    @Test
    fun `build remote browser status text should include hidden count`() {
        val entries = listOf(
            RemoteFileEntry(
                name = "Download",
                path = "/sdcard/Download",
                type = RemoteFileType.Directory,
                isHidden = false,
            ),
            RemoteFileEntry(
                name = ".secret",
                path = "/sdcard/.secret",
                type = RemoteFileType.File,
                isHidden = true,
            ),
        )

        val result = buildRemoteBrowserStatusText(entries)

        assertEquals("共 2 项，文件夹 1 个，文件 1 个，隐藏项 1 个", result)
    }
}
