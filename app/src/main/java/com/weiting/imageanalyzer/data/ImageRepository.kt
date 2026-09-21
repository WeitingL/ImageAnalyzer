package com.weiting.imageanalyzer.data

import android.content.Context
import android.database.Cursor
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

interface ImageRepository {
    /** Decodes [uri] into a bitmap an on-device model can read. Throws if the image is unreadable. */
    suspend fun load(uri: Uri): AnalyzableImage
}

class ContentResolverImageRepository(private val context: Context) : ImageRepository {

    override suspend fun load(uri: Uri): AnalyzableImage = withContext(Dispatchers.IO) {
        Timber.i("decoding image %s", uri)
        val startMs = System.currentTimeMillis()

        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longestEdge = maxOf(info.size.width, info.size.height)
            if (longestEdge > MAX_EDGE_PX) {
                val sampleSize = longestEdge / MAX_EDGE_PX
                Timber.d(
                    "source %dx%d exceeds %dpx, sampling by %d",
                    info.size.width,
                    info.size.height,
                    MAX_EDGE_PX,
                    sampleSize,
                )
                decoder.setTargetSampleSize(sampleSize)
            }
        }
        Timber.i(
            "decoded to %dx%d (%s, %d KB) in %d ms",
            bitmap.width,
            bitmap.height,
            bitmap.config,
            bitmap.allocationByteCount / 1024,
            System.currentTimeMillis() - startMs,
        )

        AnalyzableImage(
            bitmap = bitmap,
            details = ImageDetails(
                displayName = queryColumn(uri, OpenableColumns.DISPLAY_NAME) { c, i ->
                    if (c.isNull(i)) null else c.getString(i)
                },
                sizeBytes = queryColumn(uri, OpenableColumns.SIZE) { c, i ->
                    if (c.isNull(i)) null else c.getLong(i)
                },
                width = bitmap.width,
                height = bitmap.height,
            ),
        )
    }

    private fun <T> queryColumn(uri: Uri, column: String, read: (Cursor, Int) -> T?): T? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(column)
                if (index < 0) null else read(cursor, index)
            }
        }.getOrNull()

    private companion object {
        /**
         * Longest edge we keep when decoding. Large enough to stay sharp on screen, small enough
         * that a 50MP photo does not blow up the heap before a model downscales it again.
         */
        const val MAX_EDGE_PX = 2048
    }
}
