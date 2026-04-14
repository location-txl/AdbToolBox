package com.location.adbtools.adb

import com.location.adbtools.ConnectedDevice
import com.location.adbtools.RemoteFileEntry
import com.location.adbtools.RemoteFileType
import com.location.adbtools.buildConnectedDevice
import com.location.adbtools.joinRemotePath
import com.location.adbtools.normalizeRemotePath
import com.location.adbtools.sortRemoteEntries
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.pathString

/**
 * 负责定位、释放并执行内置 adb。
 *
 * 这里不做复杂抽象，桌面端只需要稳定地把打包进资源的 platform-tools 释放到本地，
 * 然后用同一个入口去执行 connect / pull / push 命令。
 */
object EmbeddedAdb {

    /**
     * adb 命令执行结果，保留退出码和原始输出，方便界面直接展示排查信息。
     *
     * @property exitCode 进程退出码，0 表示成功。
     * @property stdout 标准输出内容。
     * @property stderr 标准错误内容。
     */
    data class AdbCommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    /**
     * 记录平台资源目录和 adb 文件名，避免把平台判断散落在业务代码里。
     *
     * @property id 平台标识，会同时用于资源目录和本地释放目录。
     * @property adbFileName 当前平台的 adb 可执行文件名。
     */
    data class AdbPlatform(
        val id: String,
        val adbFileName: String,
    )

    /**
     * `adb pull` 的实时进度快照。
     *
     * @property percent 当前解析到的百分比，解析不到时为 null。
     * @property latestLine 当前最新的一段 adb 输出，已经做过 trim，适合直接展示到界面。
     */
    data class PullProgress(
        val percent: Int?,
        val latestLine: String,
    )

    /**
     * `adb devices -l` 的查询结果。
     *
     * @property devices 当前处于 `device` 状态、可直接操作的设备列表。
     * @property commandResult 原始 adb 执行结果，供界面展示和排查。
     */
    data class DeviceListResult(
        val devices: List<ConnectedDevice>,
        val commandResult: AdbCommandResult,
    )

    /**
     * 远端目录读取结果。
     *
     * @property entries 当前目录下的文件和文件夹，已按界面展示顺序排序。
     * @property commandResult 原始 adb 执行结果，供界面展示和排查。
     */
    data class RemoteEntryListResult(
        val entries: List<RemoteFileEntry>,
        val commandResult: AdbCommandResult,
    )

    private const val resourceRoot = "adb"
    private const val manifestFileName = "manifest.txt"
    private const val executablesFileName = "executables.txt"

    /**
     * 解析当前系统对应的资源目录。
     *
     * @param osName 操作系统名称，默认读取 JVM 系统属性。
     * @param osArch CPU 架构名称，默认读取 JVM 系统属性。
     * @return 命中的平台定义；如果当前平台未内置，返回 null。
     */
    fun resolvePlatform(
        osName: String = System.getProperty("os.name"),
        osArch: String = System.getProperty("os.arch"),
    ): AdbPlatform? {
        val normalizedOs = osName.lowercase()
        val normalizedArch = osArch.lowercase()
        return when {
            normalizedOs.contains("mac") && (normalizedArch == "aarch64" || normalizedArch == "arm64") ->
                AdbPlatform(id = "macos-aarch64", adbFileName = "adb")

            normalizedOs.contains("mac") && (normalizedArch == "x86_64" || normalizedArch == "amd64") ->
                AdbPlatform(id = "macos-x64", adbFileName = "adb")

            normalizedOs.contains("win") ->
                AdbPlatform(id = "windows-x64", adbFileName = "adb.exe")

            else -> null
        }
    }

    /**
     * 确保当前平台的 adb 已经释放到本地可写目录。
     *
     * @return 成功时返回本地 adb 路径；失败时返回异常，供界面直接提示。
     */
    fun ensureReady(): Result<Path> {
        val platform = resolvePlatform()
            ?: return Result.failure(IllegalStateException("未内置当前平台 adb"))
        return runCatching {
            val adbPath = localAdbPath(platform)
            if (!isExtractionComplete(platform)) {
                extractPlatformTools(platform)
            }
            adbPath.toFile().setExecutable(true, false)
            adbPath
        }
    }

    /**
     * 执行 `adb connect`。
     *
     * @param endpoint 用户输入的设备地址，格式通常为 `host:port`。
     * @return 原始命令执行结果。
     */
    fun connect(endpoint: String): AdbCommandResult {
        val adbPath = ensureReady().getOrElse { error ->
            return AdbCommandResult(exitCode = -1, stdout = "", stderr = error.message ?: "未内置当前平台 adb")
        }
        return runAdbCommand(adbPath, listOf("connect", endpoint))
    }

    /**
     * 执行 `adb pull`。
     *
     * @param serial 目标设备 serial，这里直接使用最近一次 connect 成功的 `host:port`。
     * @param remotePath 设备侧文件路径。
     * @param localPath 本地保存路径，可以是文件路径或目录路径。
     * @return 原始命令执行结果。
     */
    fun pull(
        serial: String,
        remotePath: String,
        localPath: String,
        onProgress: ((PullProgress) -> Unit)? = null,
    ): AdbCommandResult {
        val adbPath = ensureReady().getOrElse { error ->
            return AdbCommandResult(exitCode = -1, stdout = "", stderr = error.message ?: "未内置当前平台 adb")
        }
        return runAdbCommand(
            adbPath = adbPath,
            args = buildPullArguments(
                serial = serial,
                remotePath = remotePath,
                localPath = localPath,
            ),
            onOutput = { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) {
                    return@runAdbCommand
                }
                onProgress?.invoke(
                    PullProgress(
                        percent = parsePullProgressPercent(line),
                        latestLine = line,
                    ),
                )
            },
        )
    }

    /**
     * 执行 `adb push`。
     *
     * @param serial 目标设备 serial。
     * @param localPath 本地待推送文件绝对路径。
     * @param remotePath 设备侧目标目录。
     * @param onProgress 用于接收实时输出和可解析的百分比。
     * @return 原始命令执行结果。
     */
    fun push(
        serial: String,
        localPath: String,
        remotePath: String,
        onProgress: ((PullProgress) -> Unit)? = null,
    ): AdbCommandResult {
        val adbPath = ensureReady().getOrElse { error ->
            return AdbCommandResult(exitCode = -1, stdout = "", stderr = error.message ?: "未内置当前平台 adb")
        }
        return runAdbCommand(
            adbPath = adbPath,
            args = buildPushArguments(
                serial = serial,
                localPath = localPath,
                remotePath = remotePath,
            ),
            onOutput = { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) {
                    return@runAdbCommand
                }
                onProgress?.invoke(
                    PullProgress(
                        percent = parsePullProgressPercent(line),
                        latestLine = line,
                    ),
                )
            },
        )
    }

    /**
     * 在指定设备上安装单个 APK。
     *
     * 这里固定追加 `-r`，保证已安装同包名应用时执行覆盖安装，
     * 避免把安装策略分散到页面层自己拼参数。
     *
     * @param serial 目标设备 serial。
     * @param apkPath 本地 APK 绝对路径。
     * @return 原始命令执行结果。
     */
    fun installApk(serial: String, apkPath: String): AdbCommandResult {
        val adbPath = ensureReady().getOrElse { error ->
            return AdbCommandResult(exitCode = -1, stdout = "", stderr = error.message ?: "未内置当前平台 adb")
        }
        return runAdbCommand(
            adbPath = adbPath,
            args = buildInstallArguments(serial = serial, apkPath = apkPath),
        )
    }

    /**
     * 执行 `adb disconnect`。
     *
     * @param endpoint 已连接设备地址，格式通常为 `host:port`。
     * @return 原始命令执行结果。
     */
    fun disconnect(endpoint: String): AdbCommandResult {
        val adbPath = ensureReady().getOrElse { error ->
            return AdbCommandResult(exitCode = -1, stdout = "", stderr = error.message ?: "未内置当前平台 adb")
        }
        return runAdbCommand(adbPath, listOf("disconnect", endpoint))
    }

    /**
     * 查询当前 adb 已连接且可操作的设备。
     *
     * 这里统一解析 `adb devices -l` 输出，过滤掉 `offline`、`unauthorized`
     * 等不可直接操作的状态，保证上层只处理真正能用的设备。
     *
     * @return 包含设备列表和原始命令结果的结果对象。
     */
    fun listDevices(): DeviceListResult {
        val adbPath = ensureReady().getOrElse { error ->
            return DeviceListResult(
                devices = emptyList(),
                commandResult = AdbCommandResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = error.message ?: "未内置当前平台 adb",
                ),
            )
        }
        val result = runAdbCommand(adbPath, buildListDevicesArguments())
        return DeviceListResult(
            devices = parseListDevicesOutput(result.stdout),
            commandResult = result,
        )
    }

    /**
     * 读取设备目录下的一层文件列表。
     *
     * 这里显式包含隐藏文件和隐藏目录，只读取一层，避免把整个目录树直接打平。
     *
     * @param serial 目标设备 serial。
     * @param directoryPath 待读取的设备目录绝对路径。
     * @return 包含目录条目和原始命令结果的结果对象。
     */
    fun listRemoteEntries(serial: String, directoryPath: String): RemoteEntryListResult {
        val adbPath = ensureReady().getOrElse { error ->
            return RemoteEntryListResult(
                entries = emptyList(),
                commandResult = AdbCommandResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = error.message ?: "未内置当前平台 adb",
                ),
            )
        }
        val normalizedDirectoryPath = normalizeRemotePath(directoryPath)
        val result = runAdbCommand(
            adbPath = adbPath,
            args = buildListRemoteEntriesArguments(
                serial = serial,
                directoryPath = normalizedDirectoryPath,
            ),
        )
        return RemoteEntryListResult(
            entries = parseRemoteEntriesOutput(
                directoryPath = normalizedDirectoryPath,
                output = result.stdout,
            ),
            commandResult = result,
        )
    }

    /**
     * 删除设备端文件或目录。
     *
     * @param serial 目标设备 serial。
     * @param remotePath 要删除的设备绝对路径。
     * @param isDirectory 当前目标是否为目录；目录会使用递归删除。
     * @return 原始命令执行结果。
     */
    fun deleteRemoteEntry(serial: String, remotePath: String, isDirectory: Boolean): AdbCommandResult {
        val adbPath = ensureReady().getOrElse { error ->
            return AdbCommandResult(exitCode = -1, stdout = "", stderr = error.message ?: "未内置当前平台 adb")
        }
        return runAdbCommand(
            adbPath = adbPath,
            args = buildDeleteRemoteEntryArguments(
                serial = serial,
                remotePath = normalizeRemotePath(remotePath),
                isDirectory = isDirectory,
            ),
        )
    }

    /**
     * 执行 `adb kill-server`，用于应用退出前清理本地 adb 服务。
     *
     * @return 原始命令执行结果。
     */
    fun killServer(): AdbCommandResult {
        val adbPath = ensureReady().getOrElse { error ->
            return AdbCommandResult(exitCode = -1, stdout = "", stderr = error.message ?: "未内置当前平台 adb")
        }
        return runAdbCommand(adbPath, listOf("kill-server"))
    }

    /**
     * 判断 connect 是否成功。
     *
     * adb 的 connect 成功并不只返回一种固定文本，这里统一封装成一个明确规则，
     * 避免界面层重复写字符串判断。
     */
    fun isConnectSuccess(result: AdbCommandResult): Boolean {
        if (result.exitCode != 0) {
            return false
        }
        val mergedOutput = buildString {
            append(result.stdout)
            append('\n')
            append(result.stderr)
        }.lowercase()
        return mergedOutput.contains("connected to") || mergedOutput.contains("already connected to")
    }

    /**
     * 仅用于测试和错误提示，返回当前平台资源目录字符串。
     */
    fun currentPlatformResourceDirectory(): String? {
        return resolvePlatform()?.let { platformResourceDirectory(it) }
    }

    /**
     * 生成 `adb pull` 的完整命令参数，供测试验证拼装结果。
     */
    fun buildPullArguments(serial: String, remotePath: String, localPath: String): List<String> {
        return listOf("-s", serial, "pull", remotePath, localPath)
    }

    /**
     * 生成 `adb push` 的完整命令参数，供测试验证拼装结果。
     */
    fun buildPushArguments(serial: String, localPath: String, remotePath: String): List<String> {
        return listOf("-s", serial, "push", localPath, remotePath)
    }

    /**
     * 生成 `adb install -r` 的完整命令参数，供测试验证拼装结果。
     */
    fun buildInstallArguments(serial: String, apkPath: String): List<String> {
        return listOf("-s", serial, "install", "-r", apkPath)
    }

    /**
     * 生成 `adb devices -l` 的完整命令参数，供测试验证拼装结果。
     */
    fun buildListDevicesArguments(): List<String> {
        return listOf("devices", "-l")
    }

    /**
     * 生成 `adb disconnect` 的完整命令参数，供测试验证拼装结果。
     */
    fun buildDisconnectArguments(endpoint: String): List<String> {
        return listOf("disconnect", endpoint)
    }

    /**
     * 生成 `adb shell` 的完整参数，统一收口所有 shell 场景。
     */
    fun buildShellArguments(serial: String, shellCommand: String): List<String> {
        return listOf("-s", serial, "shell", "sh", "-c", shellCommand)
    }

    /**
     * 生成目录读取命令参数。
     *
     * 这里不再通过 `sh -c` 间接执行，而是直接调用 `adb shell ls ... 路径`，
     * 避免远端 shell 在 `-c` 模式下把路径参数吞掉，最终退化成列出系统根目录。
     * 同时配合 `-L` 和 `-p`，让目录符号链接也能以目录后缀 `/` 的形式返回。
     */
    fun buildListRemoteEntriesArguments(serial: String, directoryPath: String): List<String> {
        return listOf(
            "-s",
            serial,
            "shell",
            "ls",
            "-A",
            "-1",
            "-p",
            "-L",
            normalizeRemotePath(directoryPath),
        )
    }

    /**
     * 生成删除远端条目的完整参数。
     *
     * 删除这里直接调用 `adb shell rm`，不再经过 `sh -c`，
     * 避免远端 shell 再做一层解释后把目标路径吞掉。
     *
     * @param serial 目标设备 serial。
     * @param remotePath 要删除的设备绝对路径；会先做规范化。
     * @param isDirectory 当前目标是否为目录；目录会追加 `-rf`。
     * @return 可直接传给 adb 的稳定参数列表。
     */
    fun buildDeleteRemoteEntryArguments(serial: String, remotePath: String, isDirectory: Boolean): List<String> {
        val normalizedRemotePath = normalizeRemotePath(remotePath)
        val deleteFlag = if (isDirectory) "-rf" else "-f"
        return listOf(
            "-s",
            serial,
            "shell",
            "rm",
            deleteFlag,
            normalizedRemotePath,
        )
    }

    /**
     * 生成 `adb kill-server` 的完整命令参数，供测试验证拼装结果。
     */
    fun buildKillServerArguments(): List<String> {
        return listOf("kill-server")
    }

    /**
     * 从 adb 输出中提取百分比。
     *
     * adb pull / push 的实时输出格式并不稳定，这里只做保守匹配，
     * 只要行内出现 `xx%` 就当作当前进度，否则返回 null。
     *
     * @param outputLine 一段完整的 adb 输出。
     * @return 0 到 100 的整数百分比；解析不到时返回 null。
     */
    fun parsePullProgressPercent(outputLine: String): Int? {
        val match = PULL_PROGRESS_REGEX.find(outputLine) ?: return null
        return match.groupValues[1].toIntOrNull()?.takeIf { it in 0..100 }
    }

    /**
     * 解析 `adb devices -l` 的标准输出。
     *
     * @param output adb 原始标准输出。
     * @return 当前可直接操作的设备列表。
     */
    fun parseListDevicesOutput(output: String): List<ConnectedDevice> {
        return output.lineSequence()
            .mapNotNull(::parseConnectedDeviceLine)
            .toList()
    }

    /**
     * 解析远端目录输出。
     *
     * 这里解析 `ls -A -1 -p -L` 的单列输出：目录和目录符号链接
     * 都会带 `/` 后缀，普通文件则保留原样。
     *
     * @param directoryPath 当前读取的目录绝对路径。
     * @param output adb shell 标准输出。
     * @return 可直接展示到文件浏览器中的条目列表。
     */
    fun parseRemoteEntriesOutput(directoryPath: String, output: String): List<RemoteFileEntry> {
        val normalizedDirectoryPath = normalizeRemotePath(directoryPath)
        val parsedEntries = output.lineSequence()
            .mapNotNull { line -> parseRemoteEntryLine(normalizedDirectoryPath, line) }
            .toList()
        return sortRemoteEntries(parsedEntries)
    }

    private fun extractPlatformTools(platform: AdbPlatform) {
        val manifestPath = manifestPath(platform)
        val manifestLines = readResourceLines(manifestPath)
            ?: throw IllegalStateException("未找到 ${platform.id} 的内置 adb 资源清单")
        val executables = readResourceLines(executablesPath(platform)).orEmpty().toSet()
        val targetRoot = localPlatformToolsDirectory(platform)
        targetRoot.createDirectories()
        manifestLines
            .filter { it.isNotBlank() }
            .forEach { relativePath ->
                val targetPath = targetRoot.resolve(relativePath)
                targetPath.parent?.createDirectories()
                copyResourceToFile("${platformResourceDirectory(platform)}/platform-tools/$relativePath", targetPath)
                if (relativePath in executables) {
                    targetPath.toFile().setExecutable(true, false)
                }
            }
    }

    private fun runAdbCommand(
        adbPath: Path,
        args: List<String>,
        onOutput: ((String) -> Unit)? = null,
    ): AdbCommandResult {
        val process = ProcessBuilder(listOf(adbPath.pathString) + args)
            .directory(adbPath.parent.toFile())
            .start()
        val stdoutCollector = StreamCollector(process.inputStream, onOutput)
        val stderrCollector = StreamCollector(process.errorStream, onOutput)
        val stdoutThread = stdoutCollector.start()
        val stderrThread = stderrCollector.start()
        val exitCode = process.waitFor()
        stdoutThread.join()
        stderrThread.join()
        return AdbCommandResult(
            exitCode = exitCode,
            stdout = stdoutCollector.content.trim(),
            stderr = stderrCollector.content.trim(),
        )
    }

    private fun localAdbPath(platform: AdbPlatform): Path {
        return localPlatformToolsDirectory(platform).resolve(platform.adbFileName)
    }

    private fun localPlatformToolsDirectory(platform: AdbPlatform): Path {
        return Path.of(System.getProperty("user.home"), ".adbtools", platform.id, "platform-tools")
    }

    private fun manifestPath(platform: AdbPlatform): String {
        return "${platformResourceDirectory(platform)}/$manifestFileName"
    }

    private fun executablesPath(platform: AdbPlatform): String {
        return "${platformResourceDirectory(platform)}/$executablesFileName"
    }

    private fun platformResourceDirectory(platform: AdbPlatform): String {
        return "$resourceRoot/${platform.id}"
    }

    private fun readResourceLines(resourcePath: String): List<String>? {
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath) ?: return null
        return stream.bufferedReader().use { reader ->
            reader.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
    }

    private fun isExtractionComplete(platform: AdbPlatform): Boolean {
        val manifestLines = readResourceLines(manifestPath(platform))
            ?: return false
        val targetRoot = localPlatformToolsDirectory(platform)
        return manifestLines
            .filter { it.isNotBlank() }
            .all { relativePath -> targetRoot.resolve(relativePath).exists() }
    }

    private fun copyResourceToFile(resourcePath: String, targetPath: Path) {
        val inputStream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: throw IllegalStateException("缺少资源文件: $resourcePath")
        inputStream.use { input ->
            targetPath.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * 用独立线程读取进程输出，避免 `adb pull` 这类命令在输出较多时阻塞。
     */
    private class StreamCollector(
        private val inputStream: java.io.InputStream,
        private val onOutput: ((String) -> Unit)?,
    ) {
        @Volatile
        var content: String = ""
            private set

        fun start(): Thread {
            return thread(start = true) {
                val chunks = CopyOnWriteArrayList<String>()
                val segment = StringBuilder()
                inputStream.bufferedReader().use { reader ->
                    while (true) {
                        val nextChar = reader.read()
                        if (nextChar == -1) {
                            break
                        }
                        val char = nextChar.toChar()
                        chunks += char.toString()
                        if (char == '\r' || char == '\n') {
                            flushSegment(segment)
                        } else {
                            segment.append(char)
                        }
                    }
                }
                flushSegment(segment)
                content = chunks.joinToString(separator = "").trim()
            }
        }

        /**
         * adb pull 常用回车覆盖同一行展示进度，这里把回车和换行都当成一次完整输出，
         * 界面才能在命令结束前持续拿到状态。
         */
        private fun flushSegment(segment: StringBuilder) {
            if (segment.isEmpty()) {
                return
            }
            val line = segment.toString()
            segment.clear()
            onOutput?.invoke(line)
        }
    }

    private val PULL_PROGRESS_REGEX = """\b(\d{1,3})%""".toRegex()
    private val DEVICE_LINE_SPLIT_REGEX = """\s+""".toRegex()

    /**
     * 单行解析 adb 设备输出。
     *
     * adb 的设备列表会混入表头、空行和异常状态设备，这里集中做一次过滤，
     * 让页面状态层只接收真实可操作的设备。
     */
    private fun parseConnectedDeviceLine(line: String): ConnectedDevice? {
        val trimmedLine = line.trim()
        if (trimmedLine.isEmpty() || trimmedLine.startsWith("List of devices attached")) {
            return null
        }
        val tokens = trimmedLine.split(DEVICE_LINE_SPLIT_REGEX)
        if (tokens.size < 2 || tokens[1] != "device") {
            return null
        }
        return buildConnectedDevice(tokens[0])
    }

    /**
     * 单行解析 `ls -A -1 -p -L` 输出，集中处理目录后缀和绝对路径拼接。
     */
    private fun parseRemoteEntryLine(directoryPath: String, line: String): RemoteFileEntry? {
        val rawName = line.trim()
        if (rawName.isEmpty() || rawName == "." || rawName == "..") {
            return null
        }
        val isDirectory = rawName.endsWith('/')
        val name = if (isDirectory) rawName.dropLast(1) else rawName
        if (name.isBlank()) {
            return null
        }
        return RemoteFileEntry(
            name = name,
            path = joinRemotePath(directoryPath, name),
            type = if (isDirectory) RemoteFileType.Directory else RemoteFileType.File,
            isHidden = name.startsWith('.'),
        )
    }

}
