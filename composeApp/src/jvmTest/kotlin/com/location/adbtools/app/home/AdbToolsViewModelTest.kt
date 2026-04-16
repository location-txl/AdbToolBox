package com.location.adbtools.app.home

import com.location.adbtools.adb.AdbGateway
import com.location.adbtools.adb.EmbeddedAdb
import com.location.adbtools.device.buildConnectedDevice
import com.location.adbtools.filebrowser.remoteBrowserRootPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `AdbToolsViewModel` 的核心状态流转测试。
 *
 * 这里只覆盖最容易回归的主流程，避免状态改造后行为悄悄跑偏。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdbToolsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `connectDevice 成功后应刷新设备与目录状态`() = runTest {
        val endpoint = "192.168.1.10:5555"
        val fakeGateway = FakeAdbGateway().apply {
            connectResult = EmbeddedAdb.AdbCommandResult(
                exitCode = 0,
                stdout = "connected to $endpoint",
                stderr = "",
            )
            connectSuccess = true
            deviceListResult = EmbeddedAdb.DeviceListResult(
                devices = listOf(buildConnectedDevice(endpoint)),
                commandResult = EmbeddedAdb.AdbCommandResult(0, "devices", ""),
            )
            remoteEntryListResult = EmbeddedAdb.RemoteEntryListResult(
                entries = emptyList(),
                commandResult = EmbeddedAdb.AdbCommandResult(0, "ok", ""),
            )
        }
        val viewModel = AdbToolsViewModel(
            adbGateway = fakeGateway,
            ioDispatcher = dispatcher,
        )

        viewModel.connectDevice(endpoint)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(endpoint, state.endpoint)
        assertEquals(endpoint, state.currentSerial)
        assertEquals(1, state.connectedDevices.size)
        assertTrue(state.isConnected)
        assertEquals("连接成功：$endpoint", state.statusText)
        assertEquals(remoteBrowserRootPath, state.fileBrowser.currentRemoteDirectory)
        assertEquals("当前目录为空", state.fileBrowser.statusText)
        assertNull(state.busyAction)
        assertTrue(state.commandOutput.contains("connected to $endpoint"))
    }

    @Test
    fun `installApk 成功后应更新状态并发送 snackbar`() = runTest {
        val endpoint = "192.168.1.10:5555"
        val fakeGateway = FakeAdbGateway().apply {
            deviceListResult = EmbeddedAdb.DeviceListResult(
                devices = listOf(buildConnectedDevice(endpoint)),
                commandResult = EmbeddedAdb.AdbCommandResult(0, "devices", ""),
            )
            installResult = EmbeddedAdb.AdbCommandResult(
                exitCode = 0,
                stdout = "Success",
                stderr = "",
            )
        }
        val viewModel = AdbToolsViewModel(
            adbGateway = fakeGateway,
            ioDispatcher = dispatcher,
        )

        viewModel.refreshDevices()
        advanceUntilIdle()
        viewModel.updateSelectedApkPath("/tmp/demo.apk")
        val effectDeferred = async { viewModel.effects.first() }

        viewModel.installApk()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("APK 安装成功", state.install.statusText)
        assertNull(state.busyAction)
        assertTrue(state.commandOutput.contains("Success"))
        assertEquals(
            AppUiEffect.ShowSnackbar("APK 安装完成"),
            effectDeferred.await(),
        )
    }

    @Test
    fun `refreshDevices 失败后应清空旧设备并重置文件浏览器`() = runTest {
        val viewModel = AdbToolsViewModel(
            adbGateway = FakeAdbGateway().apply {
                deviceListResult = EmbeddedAdb.DeviceListResult(
                    devices = emptyList(),
                    commandResult = EmbeddedAdb.AdbCommandResult(
                        exitCode = 1,
                        stdout = "",
                        stderr = "adb unavailable",
                    ),
                )
            },
            ioDispatcher = dispatcher,
        )

        viewModel.refreshDevices()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isConnected)
        assertTrue(state.connectedDevices.isEmpty())
        assertNull(state.currentSerial)
        assertEquals("adb unavailable", state.statusText)
        assertEquals(remoteBrowserRootPath, state.fileBrowser.currentRemoteDirectory)
        assertEquals("请先连接设备后再浏览 $remoteBrowserRootPath", state.fileBrowser.statusText)
        assertNull(state.busyAction)
    }
}

/**
 * 测试用 adb 网关。
 *
 * 这里直接暴露可配置返回值，让状态流转测试只聚焦页面行为本身。
 */
private class FakeAdbGateway : AdbGateway {
    var connectResult: EmbeddedAdb.AdbCommandResult = EmbeddedAdb.AdbCommandResult(0, "", "")
    var disconnectResult: EmbeddedAdb.AdbCommandResult = EmbeddedAdb.AdbCommandResult(0, "", "")
    var deviceListResult: EmbeddedAdb.DeviceListResult = EmbeddedAdb.DeviceListResult(
        devices = emptyList(),
        commandResult = EmbeddedAdb.AdbCommandResult(0, "", ""),
    )
    var remoteEntryListResult: EmbeddedAdb.RemoteEntryListResult = EmbeddedAdb.RemoteEntryListResult(
        entries = emptyList(),
        commandResult = EmbeddedAdb.AdbCommandResult(0, "", ""),
    )
    var pullResult: EmbeddedAdb.AdbCommandResult = EmbeddedAdb.AdbCommandResult(0, "", "")
    var pushResult: EmbeddedAdb.AdbCommandResult = EmbeddedAdb.AdbCommandResult(0, "", "")
    var installResult: EmbeddedAdb.AdbCommandResult = EmbeddedAdb.AdbCommandResult(0, "", "")
    var deleteResult: EmbeddedAdb.AdbCommandResult = EmbeddedAdb.AdbCommandResult(0, "", "")
    var connectSuccess: Boolean = false

    override fun connect(endpoint: String): EmbeddedAdb.AdbCommandResult = connectResult

    override fun disconnect(endpoint: String): EmbeddedAdb.AdbCommandResult = disconnectResult

    override fun listDevices(): EmbeddedAdb.DeviceListResult = deviceListResult

    override fun listRemoteEntries(
        serial: String,
        directoryPath: String,
    ): EmbeddedAdb.RemoteEntryListResult = remoteEntryListResult

    override fun pull(
        serial: String,
        remotePath: String,
        localPath: String,
        onProgress: ((EmbeddedAdb.PullProgress) -> Unit)?,
    ): EmbeddedAdb.AdbCommandResult = pullResult

    override fun push(
        serial: String,
        localPath: String,
        remotePath: String,
        onProgress: ((EmbeddedAdb.PullProgress) -> Unit)?,
    ): EmbeddedAdb.AdbCommandResult = pushResult

    override fun installApk(serial: String, apkPath: String): EmbeddedAdb.AdbCommandResult = installResult

    override fun deleteRemoteEntry(
        serial: String,
        remotePath: String,
        isDirectory: Boolean,
    ): EmbeddedAdb.AdbCommandResult = deleteResult

    override fun isConnectSuccess(result: EmbeddedAdb.AdbCommandResult): Boolean = connectSuccess
}
