package com.stonefive.chalkak.data.post

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException

/** 사진을 줄여 디코딩하고 EXIF 방향을 픽셀에 반영한다. */
internal class OrientedBitmapDecoder(private val contentResolver: ContentResolver) {
    sealed interface Result {
        data object Unreadable : Result

        data object Failed : Result

        data class Success(val bitmap: Bitmap) : Result
    }

    /** [sampleSize]는 원본 가로·세로로 `inSampleSize`를 정한다. */
    fun decode(
        uri: Uri,
        sampleSize: (width: Int, height: Int) -> Int,
    ): Result {
        val bounds = try {
            readBounds(uri)
        } catch (error: CancellationException) {
            throw error
        } catch (_: RuntimeException) {
            return Result.Failed
        } ?: return Result.Unreadable
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return Result.Failed

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
            inScaled = false
        }
        val decoded = try {
            openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: RuntimeException) {
            null
        } ?: return Result.Failed

        val orientation = readOrientation(uri) ?: run {
            decoded.recycle()
            return Result.Failed
        }
        val oriented = try {
            decoded.applyExifOrientation(orientation)
        } catch (error: CancellationException) {
            decoded.recycle()
            throw error
        } catch (_: RuntimeException) {
            decoded.recycle()
            return Result.Failed
        }
        if (oriented !== decoded) decoded.recycle()
        return Result.Success(oriented)
    }

    private fun readBounds(uri: Uri): BitmapFactory.Options? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val input = openInputStream(uri) ?: return null
        input.use { BitmapFactory.decodeStream(it, null, options) }
        return options
    }

    private fun readOrientation(uri: Uri): Int? {
        val input = openInputStream(uri) ?: return null
        return try {
            input.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun openInputStream(uri: Uri): InputStream? = try {
        contentResolver.openInputStream(uri)
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: RuntimeException) {
        null
    }
}
