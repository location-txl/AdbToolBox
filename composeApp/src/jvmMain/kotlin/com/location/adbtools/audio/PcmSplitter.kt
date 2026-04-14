package com.location.adbtools.audio

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.outputStream

/**
 * 单个拆分输出文件的信息。
 *
 * @property channelLabel 声道标签，例如 `mic0`。
 * @property path 输出文件绝对路径。
 * @property ffplayCommand 基于固定采样参数生成的试听命令。
 */
data class SplitOutputFile(
    val channelLabel: String,
    val path: Path,
    val ffplayCommand: String,
)

/**
 * 单个输入 PCM 文件的拆分结果。
 *
 * @property inputFile 原始输入文件路径。
 * @property inputSizeBytes 输入文件大小，单位 bytes。
 * @property totalFrames 成功拆出的总帧数。
 * @property outputFiles 按声道顺序生成的输出文件列表。
 */
data class SplitFileResult(
    val inputFile: Path,
    val inputSizeBytes: Long,
    val totalFrames: Int,
    val outputFiles: List<SplitOutputFile>,
)

/**
 * 文件或目录输入的一次批量拆分结果。
 *
 * @property totalFiles 本次识别到的 PCM 文件总数。
 * @property succeededFiles 拆分成功的文件结果。
 * @property failedFiles 拆分失败的文件和失败原因。
 */
data class SplitBatchResult(
    val totalFiles: Int,
    val succeededFiles: List<SplitFileResult>,
    val failedFiles: Map<Path, String>,
)

/**
 * 把 4 声道交织 PCM 拆成单声道文件的桌面侧实现。
 *
 * 这里固定使用当前业务需要的格式：4 声道、16bit little-endian、16kHz。
 * 不做额外配置项，避免把一个稳定的小工具做成泛化框架。
 */
class PcmSplitter {

    /**
     * 处理单个文件或目录输入。
     *
     * 输入为目录时只扫描当前目录下的 `.pcm` 文件，不递归。
     * 目录批处理会尽量继续执行，并把失败项记录到返回结果里，方便桌面界面一次展示全量结果。
     *
     * @param inputPath 单个 PCM 文件路径，或包含多个 PCM 文件的目录路径。
     * @param outputDir 输出目录；不存在时会自动创建。
     * @return 本次批量处理的成功/失败明细。
     * @throws IllegalArgumentException 当输入路径无效或目录下没有 PCM 文件时抛出。
     */
    fun splitInput(inputPath: Path, outputDir: Path): SplitBatchResult {
        require(Files.exists(inputPath)) { "输入路径不存在: $inputPath" }

        if (inputPath.isRegularFile()) {
            return SplitBatchResult(
                totalFiles = 1,
                succeededFiles = listOf(splitFile(inputPath, outputDir)),
                failedFiles = emptyMap(),
            )
        }

        require(inputPath.isDirectory()) { "输入路径既不是文件也不是目录: $inputPath" }

        val pcmFiles = Files.list(inputPath).use { stream ->
            stream
                .filter { path -> path.isRegularFile() && path.name.lowercase().endsWith(".pcm") }
                .sorted(compareBy<Path> { it.name.lowercase() })
                .toList()
        }
        require(pcmFiles.isNotEmpty()) { "目录下未找到 PCM 文件: $inputPath" }

        val succeededFiles = mutableListOf<SplitFileResult>()
        val failedFiles = linkedMapOf<Path, String>()
        for (pcmFile in pcmFiles) {
            runCatching {
                splitFile(pcmFile, outputDir)
            }.onSuccess { result ->
                succeededFiles += result
            }.onFailure { error ->
                failedFiles[pcmFile] = error.message ?: "未知错误"
            }
        }
        return SplitBatchResult(
            totalFiles = pcmFiles.size,
            succeededFiles = succeededFiles,
            failedFiles = failedFiles,
        )
    }

    /**
     * 拆分单个交织 PCM 文件。
     *
     * @param inputFile 原始输入 PCM 文件。
     * @param outputDir 输出目录；不存在时会自动创建。
     * @param prefix 输出文件名前缀，默认使用输入文件 stem。
     * @return 当前输入文件的拆分结果。
     * @throws IllegalArgumentException 当文件无效或尾部存在不完整帧时抛出。
     */
    fun splitFile(
        inputFile: Path,
        outputDir: Path,
        prefix: String = inputFile.fileName.toString().substringBeforeLast('.', inputFile.fileName.toString()),
    ): SplitFileResult {
        require(Files.exists(inputFile)) { "输入文件不存在: $inputFile" }
        require(inputFile.isRegularFile()) { "输入路径不是文件: $inputFile" }

        outputDir.createDirectories()

        val inputSizeBytes = inputFile.fileSize()
        val outputPaths = CHANNEL_LABELS.map { label ->
            outputDir.resolve("$prefix.$label.pcm").toAbsolutePath().normalize()
        }
        var tail = ByteArray(0)
        var totalFrames = 0

        BufferedInputStream(inputFile.inputStream()).use { source ->
            val outputs = outputPaths.map { path ->
                BufferedOutputStream(path.outputStream())
            }
            outputs.useAll { channelOutputs ->
                val chunkBuffer = ByteArray(CHUNK_FRAMES * FRAME_BYTES)

                while (true) {
                    val bytesRead = source.read(chunkBuffer)
                    if (bytesRead < 0) {
                        break
                    }

                    val data = mergeTailAndChunk(tail = tail, chunk = chunkBuffer, chunkSize = bytesRead)
                    val fullBytes = (data.size / FRAME_BYTES) * FRAME_BYTES
                    if (fullBytes == 0) {
                        tail = data
                        continue
                    }

                    writeChannelData(
                        data = data,
                        fullBytes = fullBytes,
                        outputs = channelOutputs,
                    )
                    totalFrames += fullBytes / FRAME_BYTES
                    tail = if (fullBytes == data.size) {
                        ByteArray(0)
                    } else {
                        data.copyOfRange(fullBytes, data.size)
                    }
                }
            }
        }

        require(tail.isEmpty()) {
            "输入文件长度不是完整帧的整数倍: 剩余 ${tail.size} 字节，每帧需要 $FRAME_BYTES 字节。"
        }

        return SplitFileResult(
            inputFile = inputFile.toAbsolutePath().normalize(),
            inputSizeBytes = inputSizeBytes,
            totalFrames = totalFrames,
            outputFiles = outputPaths.mapIndexed { index, path ->
                SplitOutputFile(
                    channelLabel = CHANNEL_LABELS[index],
                    path = path,
                    ffplayCommand = buildFfplayCommand(path),
                )
            },
        )
    }

    /**
     * 把一段完整帧数据按声道拆开后写入输出流。
     *
     * 这里不在循环里逐字节写文件，而是先为每个声道拼出连续 buffer，
     * 目的是减少输出流调用次数，避免大文件拆分时出现明显的写放大。
     */
    private fun writeChannelData(
        data: ByteArray,
        fullBytes: Int,
        outputs: List<BufferedOutputStream>,
    ) {
        val frameCount = fullBytes / FRAME_BYTES
        val channelBuffers = Array(CHANNEL_LABELS.size) { ByteArray(frameCount * SAMPLE_WIDTH_BYTES) }
        var readOffset = 0

        for (frameIndex in 0 until frameCount) {
            val channelWriteOffset = frameIndex * SAMPLE_WIDTH_BYTES
            for (channelIndex in CHANNEL_LABELS.indices) {
                channelBuffers[channelIndex][channelWriteOffset] = data[readOffset]
                channelBuffers[channelIndex][channelWriteOffset + 1] = data[readOffset + 1]
                readOffset += SAMPLE_WIDTH_BYTES
            }
        }

        channelBuffers.forEachIndexed { index, channelBuffer ->
            outputs[index].write(channelBuffer)
        }
    }

    /**
     * 把上次剩余尾部和本次读取内容拼成连续字节数组。
     *
     * 只有当输入长度不是完整帧整数倍时才会出现尾部残留，这里单独处理，
     * 可以让主循环始终按“尽量处理完整帧”这个简单规则推进。
     */
    private fun mergeTailAndChunk(tail: ByteArray, chunk: ByteArray, chunkSize: Int): ByteArray {
        if (tail.isEmpty()) {
            return chunk.copyOf(chunkSize)
        }

        val merged = ByteArray(tail.size + chunkSize)
        System.arraycopy(tail, 0, merged, 0, tail.size)
        System.arraycopy(chunk, 0, merged, tail.size, chunkSize)
        return merged
    }

    /**
     * 生成固定参数的试听命令。
     *
     * 这里沿用原 Python 脚本的输出习惯，方便用户直接复制到终端快速试听。
     */
    private fun buildFfplayCommand(outputPath: Path): String {
        return "ffplay -f s16le -ar 16000 -ac 1 ${outputPath.toAbsolutePath().normalize()}"
    }

    private fun <T : AutoCloseable, R> List<T>.useAll(block: (List<T>) -> R): R {
        try {
            return block(this)
        } finally {
            asReversed().forEach { closeable ->
                runCatching { closeable.close() }
            }
        }
    }

    private companion object {
        private const val CHUNK_FRAMES = 4096
        private const val SAMPLE_WIDTH_BYTES = 2
        private const val CHANNELS = 4
        private const val FRAME_BYTES = CHANNELS * SAMPLE_WIDTH_BYTES
        private val CHANNEL_LABELS = listOf("mic0", "mic1", "ref0", "ref1")
    }
}
