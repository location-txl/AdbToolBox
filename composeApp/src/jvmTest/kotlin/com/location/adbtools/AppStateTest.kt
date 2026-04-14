package com.location.adbtools

import java.io.File
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 纯状态辅助函数测试。
 *
 * 这些函数没有外部依赖，适合用最小测试兜住边界行为。
 */
class AppStateTest {

    @Test
    fun `validateDroppedApkFiles 应只接受单个存在的 apk`() {
        val apkFile = createTempFile(suffix = ".apk").toFile()

        val result = validateDroppedApkFiles(listOf(apkFile.absolutePath))

        assertEquals(apkFile.absolutePath, result.acceptedApkPath)
        assertTrue(result.statusText.contains(apkFile.name))
    }

    @Test
    fun `validateSelectedLocalFiles 应过滤目录和无效路径`() {
        val file = createTempFile().toFile()
        val directory = createTempDirectory().toFile()
        val invalidPath = directory.toPath().resolve("missing.txt").absolutePathString()

        val result = validateSelectedLocalFiles(
            listOf(file.absolutePath, directory.absolutePath, invalidPath),
        )

        assertEquals(listOf(file.absolutePath), result.acceptedFilePaths)
        assertTrue(result.statusText.contains("已忽略 1 个文件夹"))
        assertTrue(result.statusText.contains("已忽略 1 个无效路径"))
    }

    @Test
    fun `buildRemoteBrowserStatusText 应统计目录文件和隐藏项`() {
        val result = buildRemoteBrowserStatusText(
            listOf(
                RemoteFileEntry("dir", "/sdcard/dir", RemoteFileType.Directory, false),
                RemoteFileEntry(".hidden", "/sdcard/.hidden", RemoteFileType.File, true),
                RemoteFileEntry("a.txt", "/sdcard/a.txt", RemoteFileType.File, false),
            ),
        )

        assertEquals("共 3 项，文件夹 1 个，文件 2 个，隐藏项 1 个", result)
    }

    @Test
    fun `AppUiState 应从设备列表派生连接状态`() {
        val device = buildConnectedDevice("192.168.1.10:5555")
        val state = AppUiState(
            connectedDevices = listOf(device),
            currentSerial = device.serial,
        )

        assertTrue(state.isConnected)
        assertEquals(device, state.currentDevice)
        assertNull(AppUiState().currentDevice)
    }
}
