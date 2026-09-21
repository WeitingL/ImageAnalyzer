package com.weiting.imageanalyzer.data

import android.graphics.Bitmap

/**
 * A decoded image ready for on-device inference.
 *
 * [bitmap] is deliberately a software bitmap: hardware bitmaps cannot be read back pixel by pixel,
 * which is what any local model needs.
 */
data class AnalyzableImage(
    val bitmap: Bitmap,
    val details: ImageDetails,
)

data class ImageDetails(
    val displayName: String?,
    val sizeBytes: Long?,
    val width: Int,
    val height: Int,
)
