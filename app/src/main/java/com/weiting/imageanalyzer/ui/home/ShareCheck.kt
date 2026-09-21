package com.weiting.imageanalyzer.ui.home

import android.graphics.Bitmap
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Candidate
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

/**
 * Asks Gemini Nano, on device, whether an image looks safe to post publicly.
 *
 * This is a proof of concept: a small on-device model is not a reliable content moderation system,
 * so treat the answer as a prompt for the user to think, not as a verdict to act on.
 */
private val PROMPT = """
    You are helping someone decide whether to post this photo publicly on social media.

    Write the report twice: first in English, then the same report again in Traditional
    Chinese. Keep the VERDICT value itself in English in both blocks. Use exactly this
    format and nothing else:

    --- ENGLISH ---
    CONTENT: <one sentence describing what is in the photo>
    PEOPLE: <how many recognisable faces you can see, or "none">
    VISIBLE TEXT: <any readable text that could identify a person or place, or "none">
    CONCERNS: <privacy or sensitivity concerns, or "none">
    VERDICT: <SAFE, REVIEW or AVOID> - <one short reason>

    --- 繁體中文 ---
    內容：<一句話描述照片裡有什麼>
    人物：<可辨識的人臉數量，若無則寫「無」>
    可見文字：<可能辨識出人或地點的文字，若無則寫「無」>
    風險：<隱私或敏感性方面的顧慮，若無則寫「無」>
    結論：<SAFE、REVIEW 或 AVOID> - <簡短理由>
""".trimIndent()

sealed interface ShareCheckState {
    data object Idle : ShareCheckState

    /** Asking AICore whether the feature is usable on this device. */
    data object Preparing : ShareCheckState

    /** AICore is pulling the model down. [totalBytes] is 0 until the size is known. */
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : ShareCheckState

    data object Analyzing : ShareCheckState

    data class Done(val answer: String) : ShareCheckState

    /** The device or build cannot run Gemini Nano at all. */
    data object Unsupported : ShareCheckState

    data class Failed(val message: String) : ShareCheckState
}

fun checkShareSuitability(bitmap: Bitmap): Flow<ShareCheckState> = flow {
    Timber.i("share check requested for %dx%d bitmap", bitmap.width, bitmap.height)
    emit(ShareCheckState.Preparing)

    val model = Generation.getClient(generationConfig {})
    try {
        val status = model.checkStatus()
        Timber.i("AICore feature status = %s", featureStatusName(status))

        when (status) {
            FeatureStatus.UNAVAILABLE -> {
                Timber.w("Gemini Nano unavailable on this device, giving up")
                emit(ShareCheckState.Unsupported)
                return@flow
            }

            FeatureStatus.DOWNLOADABLE -> {
                var totalBytes = 0L
                val downloadStart = System.currentTimeMillis()
                model.download().collect { status ->
                    when (status) {
                        is DownloadStatus.DownloadStarted -> {
                            totalBytes = status.bytesToDownload
                            Timber.i("model download started, %d bytes", totalBytes)
                            emit(ShareCheckState.Downloading(0, totalBytes))
                        }

                        is DownloadStatus.DownloadProgress -> {
                            Timber.v(
                                "model download %d / %d bytes",
                                status.totalBytesDownloaded,
                                totalBytes,
                            )
                            emit(
                                ShareCheckState.Downloading(
                                    status.totalBytesDownloaded,
                                    totalBytes,
                                ),
                            )
                        }

                        is DownloadStatus.DownloadFailed -> {
                            Timber.e(status.e, "model download failed")
                            throw status.e
                        }

                        is DownloadStatus.DownloadCompleted ->
                            Timber.i(
                                "model download completed in %d ms",
                                System.currentTimeMillis() - downloadStart,
                            )
                    }
                }
            }

            // Another app already triggered the download; runInference waits for it.
            FeatureStatus.DOWNLOADING -> {
                Timber.i("model already downloading, will wait")
                emit(ShareCheckState.Downloading(0, 0))
            }
        }

        emit(ShareCheckState.Analyzing)
        val inferenceStart = System.currentTimeMillis()
        val response = model.generateContent(
            generateContentRequest(ImagePart(bitmap), TextPart(PROMPT)) {
                // Low temperature: we want a consistent judgement, not a creative one.
                temperature = 0.2f
                topK = 16
                // The bilingual report is roughly twice as long as a single-language one.
                maxOutputTokens = 512
            },
        )
        val elapsedMs = System.currentTimeMillis() - inferenceStart
        val candidate = response.candidates.firstOrNull()
        val answer = candidate?.text?.trim()
        Timber.i(
            "inference finished in %d ms, %d candidate(s), %d chars, finishReason=%s",
            elapsedMs,
            response.candidates.size,
            answer?.length ?: 0,
            candidate?.finishReason,
        )
        if (candidate?.finishReason == Candidate.FinishReason.MAX_TOKENS) {
            Timber.w("answer hit maxOutputTokens, the Chinese block is probably cut off")
        }
        Timber.d("model answer:\n%s", answer)

        emit(
            if (answer.isNullOrEmpty()) {
                ShareCheckState.Failed("模型沒有回傳任何內容")
            } else {
                ShareCheckState.Done(answer)
            },
        )
    } catch (e: Exception) {
        Timber.e(e, "share check failed")
        emit(ShareCheckState.Failed(e.message ?: e::class.java.simpleName))
    } finally {
        model.close()
        Timber.d("generative model closed")
    }
}

private fun featureStatusName(status: Int): String = when (status) {
    FeatureStatus.UNAVAILABLE -> "UNAVAILABLE"
    FeatureStatus.DOWNLOADABLE -> "DOWNLOADABLE"
    FeatureStatus.DOWNLOADING -> "DOWNLOADING"
    FeatureStatus.AVAILABLE -> "AVAILABLE"
    else -> "UNKNOWN($status)"
}
