package com.weiting.imageanalyzer.data

import android.graphics.Bitmap
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Candidate
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import timber.log.Timber

/**
 * Runs the check entirely on device through AICore, so there is no API key and nothing leaves the
 * phone.
 *
 * A small on-device model is not a reliable content moderation system: treat the answer as
 * something to make the user think, not a verdict to act on.
 */
class GeminiNanoShareCheckRepository : ShareCheckRepository {

    override fun check(bitmap: Bitmap): Flow<ShareCheckStatus> = flow {
        Timber.i("share check requested for %dx%d bitmap", bitmap.width, bitmap.height)
        emit(ShareCheckStatus.Preparing)

        val model = Generation.getClient(generationConfig {})
        try {
            val status = model.checkStatus()
            Timber.i("AICore feature status = %s", featureStatusName(status))

            when (status) {
                FeatureStatus.UNAVAILABLE -> {
                    Timber.w("Gemini Nano unavailable on this device, giving up")
                    emit(ShareCheckStatus.Unsupported)
                    return@flow
                }

                FeatureStatus.DOWNLOADABLE -> downloadModel(model)

                // Another app already triggered the download; inference waits for it.
                FeatureStatus.DOWNLOADING -> {
                    Timber.i("model already downloading, will wait")
                    emit(ShareCheckStatus.Downloading(0, 0))
                }
            }

            emit(ShareCheckStatus.Analyzing)
            val request = generateContentRequest(ImagePart(bitmap), TextPart(PROMPT)) {
                // Low temperature: we want a consistent judgement, not a creative one.
                temperature = 0.2f
                topK = 16
                // A ceiling to stop runaway generation, not a speed dial: the model usually stops
                // well before this, and lowering it only helps once it starts truncating.
                maxOutputTokens = 256
            }
            // Fixed prefill cost, dominated by the image rather than the prompt. This is the part
            // of the latency that shortening the answer cannot reduce.
            Timber.i(
                "input = %d tokens (device limit %d)",
                model.countTokens(request).totalTokens,
                model.getTokenLimit(),
            )

            val inferenceStart = System.currentTimeMillis()
            val response = model.generateContent(request)
            val candidate = response.candidates.firstOrNull()
            val answer = candidate?.text?.trim()
            Timber.i(
                "inference finished in %d ms, %d candidate(s), %d chars, finishReason=%s",
                System.currentTimeMillis() - inferenceStart,
                response.candidates.size,
                answer?.length ?: 0,
                candidate?.finishReason,
            )
            if (candidate?.finishReason == Candidate.FinishReason.MAX_TOKENS) {
                Timber.w("answer was cut off by maxOutputTokens, raise it")
            }
            Timber.d("model answer:\n%s", answer)

            emit(
                if (answer.isNullOrEmpty()) {
                    ShareCheckStatus.Failed("模型沒有回傳任何內容")
                } else {
                    ShareCheckStatus.Done(answer)
                },
            )
        } catch (e: Exception) {
            Timber.e(e, "share check failed")
            emit(ShareCheckStatus.Failed(e.message ?: e::class.java.simpleName))
        } finally {
            model.close()
            Timber.d("generative model closed")
        }
    }

    private suspend fun FlowCollector<ShareCheckStatus>.downloadModel(model: GenerativeModel) {
        var totalBytes = 0L
        val startMs = System.currentTimeMillis()
        model.download().collect { status ->
            when (status) {
                is DownloadStatus.DownloadStarted -> {
                    totalBytes = status.bytesToDownload
                    Timber.i("model download started, %d bytes", totalBytes)
                    emit(ShareCheckStatus.Downloading(0, totalBytes))
                }

                is DownloadStatus.DownloadProgress -> {
                    Timber.v(
                        "model download %d / %d bytes",
                        status.totalBytesDownloaded,
                        totalBytes,
                    )
                    emit(ShareCheckStatus.Downloading(status.totalBytesDownloaded, totalBytes))
                }

                is DownloadStatus.DownloadFailed -> {
                    Timber.e(status.e, "model download failed")
                    throw status.e
                }

                is DownloadStatus.DownloadCompleted ->
                    Timber.i(
                        "model download completed in %d ms",
                        System.currentTimeMillis() - startMs,
                    )
            }
        }
    }

    private fun featureStatusName(status: Int): String = when (status) {
        FeatureStatus.UNAVAILABLE -> "UNAVAILABLE"
        FeatureStatus.DOWNLOADABLE -> "DOWNLOADABLE"
        FeatureStatus.DOWNLOADING -> "DOWNLOADING"
        FeatureStatus.AVAILABLE -> "AVAILABLE"
        else -> "UNKNOWN($status)"
    }

    private companion object {
        /** Fixed and deliberately not user-editable: the app asks the question, not the user. */
        val PROMPT = """
            You are helping someone decide whether to post this photo publicly on social media.

            Look at the image and answer in exactly this format, one item per line. Keep every
            answer short.

            CONTENT: <one sentence describing what is in the photo>
            PEOPLE: <how many recognisable faces you can see, or "none">
            VISIBLE TEXT: <any readable text that could identify a person or place, or "none">
            CONCERNS: <privacy or sensitivity concerns, or "none">
            VERDICT: <SAFE, REVIEW or AVOID> - <one short reason>
        """.trimIndent()
    }
}
