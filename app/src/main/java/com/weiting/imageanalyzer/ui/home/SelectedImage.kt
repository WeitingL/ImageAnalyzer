package com.weiting.imageanalyzer.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Longest edge we keep when decoding. Large enough to stay sharp on screen, small enough that a
 * 50MP photo does not blow up the heap before a model gets to downscale it again.
 */
private const val MAX_EDGE_PX = 2048

/** What the home screen knows about the image the user picked. */
sealed interface ImageUiState {
    data object Empty : ImageUiState

    data object Loading : ImageUiState

    data class Ready(
        val bitmap: ImageBitmap,
        /** Kept around so on-device models can read pixels without a second decode. */
        val source: Bitmap,
        val details: ImageDetails,
    ) : ImageUiState

    data object Failed : ImageUiState
}

data class ImageDetails(
    val displayName: String?,
    val sizeBytes: Long?,
    val width: Int,
    val height: Int,
)

/** Decodes [uri] into a software bitmap that both Compose and on-device models can read. */
suspend fun loadImage(context: Context, uri: Uri): ImageUiState = withContext(Dispatchers.IO) {
    runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Hardware bitmaps cannot be read back pixel by pixel, which any local inference needs.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longestEdge = maxOf(info.size.width, info.size.height)
            if (longestEdge > MAX_EDGE_PX) {
                decoder.setTargetSampleSize(longestEdge / MAX_EDGE_PX)
            }
        }
        ImageUiState.Ready(
            bitmap = bitmap.asImageBitmap(),
            source = bitmap,
            details = ImageDetails(
                displayName = queryDisplayName(context, uri),
                sizeBytes = querySize(context, uri),
                width = bitmap.width,
                height = bitmap.height,
            ),
        )
    }.getOrElse {
        // A picker grant does not survive process death, so a restored Uri can legitimately fail.
        ImageUiState.Failed
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? =
    queryColumn(context, uri, OpenableColumns.DISPLAY_NAME) { cursor, index ->
        cursor.getStringOrNull(index)
    }

private fun querySize(context: Context, uri: Uri): Long? =
    queryColumn(context, uri, OpenableColumns.SIZE) { cursor, index ->
        if (cursor.isNull(index)) null else cursor.getLong(index)
    }

private fun <T> queryColumn(
    context: Context,
    uri: Uri,
    column: String,
    read: (android.database.Cursor, Int) -> T?,
): T? = runCatching {
    context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(column)
        if (index < 0) null else read(cursor, index)
    }
}.getOrNull()

private fun android.database.Cursor.getStringOrNull(index: Int): String? =
    if (isNull(index)) null else getString(index)
