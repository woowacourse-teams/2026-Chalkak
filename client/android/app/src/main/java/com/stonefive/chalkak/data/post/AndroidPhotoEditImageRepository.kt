package com.stonefive.chalkak.data.post

import android.content.ContentResolver
import android.net.Uri
import com.stonefive.chalkak.domain.model.photoedit.PhotoPixels
import com.stonefive.chalkak.domain.repository.PhotoEditImageRepository
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidPhotoEditImageRepository(contentResolver: ContentResolver) : PhotoEditImageRepository {
    private val decoder = OrientedBitmapDecoder(contentResolver)

    override suspend fun loadPixels(
        imageUri: String,
        minLongEdge: Int,
    ): PhotoPixels? = withContext(Dispatchers.IO) {
        try {
            decodePixels(Uri.parse(imageUri), minLongEdge)
        } catch (error: CancellationException) {
            throw error
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun decodePixels(
        uri: Uri,
        minLongEdge: Int,
    ): PhotoPixels? {
        val decoded = decoder.decode(uri) { width, height -> sampleSizeKeeping(max(width, height), minLongEdge) }
        val bitmap = (decoded as? OrientedBitmapDecoder.Result.Success)?.bitmap ?: return null
        return try {
            val argb = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            PhotoPixels(bitmap.width, bitmap.height, argb)
        } finally {
            bitmap.recycle()
        }
    }

    private fun sampleSizeKeeping(
        longEdge: Int,
        minLongEdge: Int,
    ): Int {
        var sampleSize = 1
        while (longEdge / (sampleSize * 2) >= minLongEdge) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
