package com.stonefive.chalkak.data.post

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.stonefive.chalkak.domain.model.PhotoPixels
import com.stonefive.chalkak.domain.repository.PhotoEditImageRepository
import java.io.IOException
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidPhotoEditImageRepository(private val contentResolver: ContentResolver) : PhotoEditImageRepository {
    override suspend fun loadPixels(
        imageUri: String,
        minLongEdge: Int,
    ): PhotoPixels? = withContext(Dispatchers.IO) {
        try {
            decodePixels(Uri.parse(imageUri), minLongEdge)
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun decodePixels(
        uri: Uri,
        minLongEdge: Int,
    ): PhotoPixels? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // 크기만 읽을 때는 비트맵 없이 null을 돌려주므로 스트림을 열었는지만 확인한다.
        val input = contentResolver.openInputStream(uri) ?: return null
        input.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return null

        var sampleSize = 1
        while (longEdge / (sampleSize * 2) >= minLongEdge) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        val orientation = contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val oriented = decoded.applyExifOrientation(orientation)
        if (oriented !== decoded) decoded.recycle()

        val argb = IntArray(oriented.width * oriented.height)
        oriented.getPixels(argb, 0, oriented.width, 0, 0, oriented.width, oriented.height)
        val pixels = PhotoPixels(oriented.width, oriented.height, argb)
        oriented.recycle()
        return pixels
    }
}
