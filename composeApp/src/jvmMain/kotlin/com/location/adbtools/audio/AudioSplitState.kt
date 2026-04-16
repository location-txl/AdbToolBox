package com.location.adbtools.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.location.adbtools.desktop.selectDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * 音频拆分区域的界面状态。
 *
 * 这里单独维护输入、输出和执行结果，避免继续把非 ADB 状态塞进主页面状态对象。
 */
@Stable
class AudioSplitUiState {

    /** 用户选择的 PCM 文件或目录路径。 */
    var inputPath by mutableStateOf("")

    /** 用户选择的输出目录路径。 */
    var outputDirectory by mutableStateOf("")

    /** 当前音频拆分是否仍在执行。 */
    var isRunning by mutableStateOf(false)

    /** 音频拆分区域顶部展示的状态文本。 */
    var statusText by mutableStateOf("选择 PCM 文件或目录后开始拆分")

    /** 最近一次拆分结果的详细文本。 */
    var resultText by mutableStateOf("尚未执行拆分")
}

/**
 * 音频拆分区域的动作集合。
 *
 * 职责只有两类：管理文件选择和驱动拆分执行。
 * 复杂的 PCM 字节处理仍留在 [PcmSplitter]，这里不重复实现业务细节。
 */
class AudioSplitActions(
    /** 当前音频拆分区域状态，会被动作直接修改。 */
    private val uiState: AudioSplitUiState,
    /** 页面级协程作用域，用于执行后台拆分。 */
    private val scope: CoroutineScope,
    /** 真正的 PCM 拆分实现。 */
    private val splitter: PcmSplitter = PcmSplitter(),
) {




    /**
     * 执行一次 PCM 拆分。
     *
     * 这里只做输入校验、状态切换和结果回填。
     * 目录批处理内部的成功/失败细节统一交给 [PcmSplitter] 返回，避免 UI 动作层再管理一套文件级状态机。
     */
    fun splitAudio() {
        val trimmedInputPath = uiState.inputPath.trim()
        val trimmedOutputDirectory = uiState.outputDirectory.trim()

        when {
            trimmedInputPath.isEmpty() -> {
                uiState.statusText = "请先选择输入文件或目录"
                return
            }

            trimmedOutputDirectory.isEmpty() -> {
                uiState.statusText = "请先选择输出目录"
                return
            }
        }

        uiState.isRunning = true
        uiState.statusText = "正在拆分 PCM..."
        uiState.resultText = "处理中，请稍候..."

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    splitter.splitInput(
                        inputPath = Path.of(trimmedInputPath),
                        outputDir = Path.of(trimmedOutputDirectory),
                    )
                }
            }.onSuccess { result ->
                uiState.statusText = buildStatusText(result)
                uiState.resultText = formatSplitBatchResult(result)
            }.onFailure { error ->
                uiState.statusText = error.message ?: "拆分失败"
                uiState.resultText = buildString {
                    appendLine("拆分失败")
                    appendLine()
                    append(error.message ?: "未知错误")
                }
            }
            uiState.isRunning = false
        }
    }

    /**
     * 汇总批处理结果对应的状态文案。
     */
    private fun buildStatusText(result: SplitBatchResult): String {
        val successCount = result.succeededFiles.size
        val failureCount = result.failedFiles.size
        return when {
            failureCount == 0 -> "拆分完成：成功处理 $successCount 个文件"
            successCount == 0 -> "拆分完成：全部失败，共 $failureCount 个文件"
            else -> "拆分完成：成功 $successCount 个，失败 $failureCount 个"
        }
    }

    /**
     * 把批量结果格式化成适合桌面文本面板直接展示的内容。
     */
    private fun formatSplitBatchResult(result: SplitBatchResult): String {
        return buildString {
            appendLine("输入文件总数: ${result.totalFiles}")
            appendLine("成功: ${result.succeededFiles.size}")
            appendLine("失败: ${result.failedFiles.size}")

            if (result.succeededFiles.isNotEmpty()) {
                appendLine()
                appendLine("成功文件:")
                result.succeededFiles.forEach { fileResult ->
                    appendLine("- 输入文件: ${fileResult.inputFile}")
                    appendLine("  输入大小: ${fileResult.inputSizeBytes} bytes")
                    appendLine("  总帧数: ${fileResult.totalFrames}")
                    appendLine("  输出文件:")
                    fileResult.outputFiles.forEach { outputFile ->
                        appendLine("    - ${outputFile.channelLabel}: ${outputFile.path}")
                        appendLine("      ${outputFile.ffplayCommand}")
                    }
                }
            }

            if (result.failedFiles.isNotEmpty()) {
                appendLine()
                appendLine("失败文件:")
                result.failedFiles.forEach { (path, message) ->
                    appendLine("- $path")
                    appendLine("  $message")
                }
            }
        }.trim()
    }
}

/**
 * 记住音频拆分区域状态对象。
 */
@Composable
fun rememberAudioSplitUiState(): AudioSplitUiState {
    return remember { AudioSplitUiState() }
}

/**
 * 记住音频拆分区域动作对象。
 *
 * @param uiState 当前音频拆分状态。
 * @param scope 页面级协程作用域。
 */
@Composable
fun rememberAudioSplitActions(
    uiState: AudioSplitUiState,
    scope: CoroutineScope,
): AudioSplitActions {
    return remember(uiState, scope) {
        AudioSplitActions(
            uiState = uiState,
            scope = scope,
        )
    }
}
