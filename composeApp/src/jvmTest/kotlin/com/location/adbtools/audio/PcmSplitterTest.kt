package com.location.adbtools.audio

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 覆盖 PCM 拆分的核心行为，避免声道顺序、目录扫描和尾部校验在重构时被改坏。
 */
class PcmSplitterTest {

    private val splitter = PcmSplitter()

    @Test
    fun `split file should separate interleaved four channel pcm in channel order`() {
        val tempDir = createTempDirectory("pcm-split-file")
        val inputFile = tempDir.resolve("sample.pcm")
        val outputDir = tempDir.resolve("out")

        inputFile.writeBytes(
            byteArrayOf(
                1, 2,
                3, 4,
                5, 6,
                7, 8,
                9, 10,
                11, 12,
                13, 14,
                15, 16,
            ),
        )

        val result = splitter.splitFile(inputFile, outputDir)

        assertEquals(2, result.totalFrames)
        assertEquals("sample.pcm", result.inputFile.fileName.toString())
        assertEquals(
            byteArrayOf(1, 2, 9, 10).toList(),
            outputDir.resolve("sample.mic0.pcm").readBytes().toList(),
        )
        assertEquals(
            byteArrayOf(3, 4, 11, 12).toList(),
            outputDir.resolve("sample.mic1.pcm").readBytes().toList(),
        )
        assertEquals(
            byteArrayOf(5, 6, 13, 14).toList(),
            outputDir.resolve("sample.ref0.pcm").readBytes().toList(),
        )
        assertEquals(
            byteArrayOf(7, 8, 15, 16).toList(),
            outputDir.resolve("sample.ref1.pcm").readBytes().toList(),
        )
    }

    @Test
    fun `split file should use input stem as output prefix and fixed ffplay command`() {
        val tempDir = createTempDirectory("pcm-split-prefix")
        val inputFile = tempDir.resolve("voice_record.pcm")
        val outputDir = tempDir.resolve("out")

        inputFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))

        val result = splitter.splitFile(inputFile, outputDir)

        assertEquals(
            listOf(
                "voice_record.mic0.pcm",
                "voice_record.mic1.pcm",
                "voice_record.ref0.pcm",
                "voice_record.ref1.pcm",
            ),
            result.outputFiles.map { it.path.fileName.toString() },
        )
        assertTrue(
            result.outputFiles.all { output ->
                output.ffplayCommand == "ffplay -f s16le -ar 16000 -ac 1 ${output.path}"
            },
        )
    }

    @Test
    fun `split input should only process current directory pcm files in sorted order`() {
        val tempDir = createTempDirectory("pcm-split-dir")
        val inputDir = tempDir.resolve("input").createDirectories()
        val nestedDir = inputDir.resolve("nested").createDirectories()
        inputDir.resolve("b_test.pcm").writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        inputDir.resolve("a_test.pcm").writeBytes(byteArrayOf(11, 12, 13, 14, 15, 16, 17, 18))
        inputDir.resolve("ignore.txt").writeBytes(byteArrayOf(1))
        nestedDir.resolve("nested.pcm").writeBytes(byteArrayOf(21, 22, 23, 24, 25, 26, 27, 28))
        val outputDir = tempDir.resolve("out")

        val result = splitter.splitInput(inputDir, outputDir)

        assertEquals(2, result.totalFiles)
        assertEquals(
            listOf("a_test.pcm", "b_test.pcm"),
            result.succeededFiles.map { it.inputFile.fileName.toString() },
        )
        assertTrue(result.failedFiles.isEmpty())
        assertTrue(outputDir.resolve("nested.mic0.pcm").toFile().exists().not())
    }

    @Test
    fun `split input should fail when directory contains no pcm file`() {
        val tempDir = createTempDirectory("pcm-split-empty")
        val inputDir = tempDir.resolve("input").createDirectories()

        val error = assertFailsWith<IllegalArgumentException> {
            splitter.splitInput(inputDir, tempDir.resolve("out"))
        }

        assertEquals("目录下未找到 PCM 文件: $inputDir", error.message)
    }

    @Test
    fun `split file should fail when tail bytes are not a full frame`() {
        val tempDir = createTempDirectory("pcm-split-tail")
        val inputFile = tempDir.resolve("broken.pcm")

        inputFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7))

        val error = assertFailsWith<IllegalArgumentException> {
            splitter.splitFile(inputFile, tempDir.resolve("out"))
        }

        assertEquals(
            "输入文件长度不是完整帧的整数倍: 剩余 7 字节，每帧需要 8 字节。",
            error.message,
        )
    }

    @Test
    fun `split input should keep failed file details when batch contains invalid pcm`() {
        val tempDir = createTempDirectory("pcm-split-batch")
        val inputDir = tempDir.resolve("input").createDirectories()
        val outputDir = tempDir.resolve("out")
        val goodFile = inputDir.resolve("a_good.pcm")
        val badFile = inputDir.resolve("b_bad.pcm")

        goodFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        badFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        val result = splitter.splitInput(inputDir, outputDir)

        assertEquals(2, result.totalFiles)
        assertEquals(listOf("a_good.pcm"), result.succeededFiles.map { it.inputFile.fileName.toString() })
        assertEquals(
            "输入文件长度不是完整帧的整数倍: 剩余 5 字节，每帧需要 8 字节。",
            result.failedFiles[badFile],
        )
    }
}
