package com.location.adbtools.adb

import com.location.adbtools.device.DeviceConnectionType
import com.location.adbtools.device.buildConnectedDevice
import com.location.adbtools.filebrowser.RemoteFileType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 覆盖 adb 平台映射和命令结果判断，避免平台判断改坏后打包出来不能用。
 */
class EmbeddedAdbTest {

    @Test
    fun `build connected device should mark serial as usb`() {
        val device = buildConnectedDevice("R58M123ABC")

        assertEquals("R58M123ABC", device.serial)
        assertEquals("USB · R58M123ABC", device.displayName)
        assertEquals(DeviceConnectionType.Usb, device.connectionType)
    }

    @Test
    fun `build connected device should mark host port as network`() {
        val device = buildConnectedDevice("192.168.1.10:5555")

        assertEquals("192.168.1.10:5555", device.serial)
        assertEquals("网络 · 192.168.1.10:5555", device.displayName)
        assertEquals(DeviceConnectionType.Network, device.connectionType)
    }

    @Test
    fun `mac arm64 should map to macos aarch64 resource directory`() {
        val platform = EmbeddedAdb.resolvePlatform(osName = "Mac OS X", osArch = "aarch64")

        assertEquals("macos-aarch64", platform?.id)
        assertEquals("adb", platform?.adbFileName)
    }

    @Test
    fun `mac x64 should map to macos x64 resource directory`() {
        val platform = EmbeddedAdb.resolvePlatform(osName = "Mac OS X", osArch = "x86_64")

        assertEquals("macos-x64", platform?.id)
        assertEquals("adb", platform?.adbFileName)
    }

    @Test
    fun `connect result should accept connected to output`() {
        val result = EmbeddedAdb.AdbCommandResult(
            exitCode = 0,
            stdout = "connected to 192.168.1.10:5555",
            stderr = "",
        )

        assertTrue(EmbeddedAdb.isConnectSuccess(result))
    }

    @Test
    fun `connect result should accept already connected output`() {
        val result = EmbeddedAdb.AdbCommandResult(
            exitCode = 0,
            stdout = "already connected to 192.168.1.10:5555",
            stderr = "",
        )

        assertTrue(EmbeddedAdb.isConnectSuccess(result))
    }

    @Test
    fun `connect result should reject non zero exit code`() {
        val result = EmbeddedAdb.AdbCommandResult(
            exitCode = 1,
            stdout = "connected to 192.168.1.10:5555",
            stderr = "",
        )

        assertFalse(EmbeddedAdb.isConnectSuccess(result))
    }

    @Test
    fun `pull arguments should always include device serial`() {
        val args = EmbeddedAdb.buildPullArguments(
            serial = "192.168.1.10:5555",
            remotePath = "/sdcard/demo.txt",
            localPath = "/Users/tianxiaolong/Downloads/demo.txt",
        )

        assertEquals(
            listOf(
                "-s",
                "192.168.1.10:5555",
                "pull",
                "/sdcard/demo.txt",
                "/Users/tianxiaolong/Downloads/demo.txt",
            ),
            args,
        )
    }

    @Test
    fun `install arguments should always use replace flag and preserve apk path`() {
        val args = EmbeddedAdb.buildInstallArguments(
            serial = "R58M123ABC",
            apkPath = """C:\Users\tttx0\Downloads\demo app.apk""",
        )

        assertEquals(
            listOf(
                "-s",
                "R58M123ABC",
                "install",
                "-r",
                """C:\Users\tttx0\Downloads\demo app.apk""",
            ),
            args,
        )
    }

    @Test
    fun `push arguments should always include device serial and remote directory`() {
        val args = EmbeddedAdb.buildPushArguments(
            serial = "R58M123ABC",
            localPath = """C:\Users\tttx0\Downloads\demo file.txt""",
            remotePath = "/sdcard/Download",
        )

        assertEquals(
            listOf(
                "-s",
                "R58M123ABC",
                "push",
                """C:\Users\tttx0\Downloads\demo file.txt""",
                "/sdcard/Download",
            ),
            args,
        )
    }

    @Test
    fun `disconnect arguments should target current endpoint`() {
        val args = EmbeddedAdb.buildDisconnectArguments("192.168.1.10:5555")

        assertEquals(
            listOf("disconnect", "192.168.1.10:5555"),
            args,
        )
    }

    @Test
    fun `shell arguments should keep serial and script together`() {
        val args = EmbeddedAdb.buildShellArguments(
            serial = "R58M123ABC",
            shellCommand = "ls /sdcard",
        )

        assertEquals(
            listOf("-s", "R58M123ABC", "shell", "sh", "-c", "ls /sdcard"),
            args,
        )
    }

    @Test
    fun `list devices arguments should use adb devices long format`() {
        assertEquals(
            listOf("devices", "-l"),
            EmbeddedAdb.buildListDevicesArguments(),
        )
    }

    @Test
    fun `kill server arguments should be stable`() {
        assertEquals(
            listOf("kill-server"),
            EmbeddedAdb.buildKillServerArguments(),
        )
    }

    @Test
    fun `delete remote file arguments should use direct rm command`() {
        val args = EmbeddedAdb.buildDeleteRemoteEntryArguments(
            serial = "R58M123ABC",
            remotePath = "/sdcard/demo file.txt",
            isDirectory = false,
        )

        assertEquals(
            listOf(
                "-s",
                "R58M123ABC",
                "shell",
                "rm",
                "-f",
                "/sdcard/demo file.txt",
            ),
            args,
        )
    }

    @Test
    fun `delete remote directory arguments should use recursive deletion`() {
        val args = EmbeddedAdb.buildDeleteRemoteEntryArguments(
            serial = "R58M123ABC",
            remotePath = "/sdcard/Download",
            isDirectory = true,
        )

        assertEquals(
            listOf(
                "-s",
                "R58M123ABC",
                "shell",
                "rm",
                "-rf",
                "/sdcard/Download",
            ),
            args,
        )
    }

    @Test
    fun `delete remote arguments should normalize blank path to sdcard root`() {
        val args = EmbeddedAdb.buildDeleteRemoteEntryArguments(
            serial = "R58M123ABC",
            remotePath = "   ",
            isDirectory = true,
        )

        assertEquals(
            listOf(
                "-s",
                "R58M123ABC",
                "shell",
                "rm",
                "-rf",
                "/sdcard",
            ),
            args,
        )
    }

    @Test
    fun `windows embedded adb resources should include manifest and adb executable`() {
        val classLoader = javaClass.classLoader

        val manifestStream = classLoader.getResourceAsStream("adb/windows-x64/manifest.txt")
        val adbStream = classLoader.getResourceAsStream("adb/windows-x64/platform-tools/adb.exe")

        assertNotNull(manifestStream, "windows-x64 manifest.txt missing")
        assertNotNull(adbStream, "windows-x64 adb.exe missing")
    }

    @Test
    fun `pull progress parser should extract percent from adb output`() {
        val percent = EmbeddedAdb.parsePullProgressPercent("[ 42%] /sdcard/demo.txt")

        assertEquals(42, percent)
    }

    @Test
    fun `pull progress parser should return null when output has no percent`() {
        val percent = EmbeddedAdb.parsePullProgressPercent("pulling from device...")

        assertEquals(null, percent)
    }

    @Test
    fun `list devices parser should keep usb device`() {
        val devices = EmbeddedAdb.parseListDevicesOutput(
            """
            List of devices attached
            R58M123ABC device usb:1-1 product:demo model:Pixel device:demo transport_id:1
            """.trimIndent(),
        )

        assertEquals(1, devices.size)
        assertEquals("R58M123ABC", devices[0].serial)
        assertEquals("USB · R58M123ABC", devices[0].displayName)
        assertEquals(DeviceConnectionType.Usb, devices[0].connectionType)
    }

    @Test
    fun `list devices parser should keep usb and network devices`() {
        val devices = EmbeddedAdb.parseListDevicesOutput(
            """
            List of devices attached
            R58M123ABC device usb:1-1 product:demo model:Pixel device:demo transport_id:1
            192.168.1.10:5555 device product:demo model:Pixel device:demo transport_id:2
            """.trimIndent(),
        )

        assertEquals(2, devices.size)
        assertEquals(DeviceConnectionType.Usb, devices[0].connectionType)
        assertEquals(DeviceConnectionType.Network, devices[1].connectionType)
        assertEquals("网络 · 192.168.1.10:5555", devices[1].displayName)
    }

    @Test
    fun `list devices parser should ignore non device states`() {
        val devices = EmbeddedAdb.parseListDevicesOutput(
            """
            List of devices attached
            R58M123ABC offline usb:1-1 product:demo model:Pixel device:demo transport_id:1
            192.168.1.10:5555 unauthorized transport_id:2
            """.trimIndent(),
        )

        assertTrue(devices.isEmpty())
    }

    @Test
    fun `list remote entries parser should keep hidden files and folders`() {
        val entries = EmbeddedAdb.parseRemoteEntriesOutput(
            directoryPath = "/sdcard",
            output = """
                Download/
                note.txt
                .config/
                .hidden
            """.trimIndent(),
        )

        assertEquals(4, entries.size)
        assertEquals(RemoteFileType.Directory, entries[0].type)
        assertEquals(".config", entries[0].name)
        assertTrue(entries[0].isHidden)
        assertEquals("/sdcard/.hidden", entries[2].path)
    }

    @Test
    fun `list remote entries parser should ignore dot directories`() {
        val entries = EmbeddedAdb.parseRemoteEntriesOutput(
            directoryPath = "/sdcard",
            output = """
                .
                ..
                demo.txt
            """.trimIndent(),
        )

        assertEquals(1, entries.size)
        assertEquals("demo.txt", entries.single().name)
    }

    @Test
    fun `list remote entries parser should keep names with spaces`() {
        val entries = EmbeddedAdb.parseRemoteEntriesOutput(
            directoryPath = "/sdcard/Download",
            output = "demo file.txt",
        )

        assertEquals(1, entries.size)
        assertEquals("/sdcard/Download/demo file.txt", entries.single().path)
    }

    @Test
    fun `list remote entries arguments should use direct ls with target path`() {
        val args = EmbeddedAdb.buildListRemoteEntriesArguments(
            serial = "R58M123ABC",
            directoryPath = "/sdcard/Download",
        )

        assertEquals(
            listOf("-s", "R58M123ABC", "shell", "ls", "-A", "-1", "-p", "-L", "/sdcard/Download"),
            args,
        )
    }

    @Test
    fun `list remote entries parser should treat symlinked directory output as directory`() {
        val entries = EmbeddedAdb.parseRemoteEntriesOutput(
            directoryPath = "/sdcard",
            output = "linkedDir/",
        )

        assertEquals(1, entries.size)
        assertEquals(RemoteFileType.Directory, entries.single().type)
        assertEquals("/sdcard/linkedDir", entries.single().path)
    }
}
