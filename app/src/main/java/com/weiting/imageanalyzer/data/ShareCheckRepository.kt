package com.weiting.imageanalyzer.data

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow

/** Progress of a single share-suitability check, from availability probe to answer. */
sealed interface ShareCheckStatus {
    data object Idle : ShareCheckStatus

    /** Asking the runtime whether the model is usable on this device. */
    data object Preparing : ShareCheckStatus

    /** The model is being fetched. [totalBytes] is 0 until the size is known. */
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : ShareCheckStatus

    data object Analyzing : ShareCheckStatus

    data class Done(val answer: String) : ShareCheckStatus

    /** This device or build cannot run the model at all. */
    data object Unsupported : ShareCheckStatus

    data class Failed(val message: String) : ShareCheckStatus
}

/**
 * Judges whether an image looks safe to post publicly.
 *
 * Kept behind an interface so the on-device backend can be swapped (Gemini Nano today, a MediaPipe
 * or LiteRT model later) without the ViewModel or UI changing.
 */
interface ShareCheckRepository {
    fun check(bitmap: Bitmap): Flow<ShareCheckStatus>
}
