package com.stonefive.chalkak.data.post

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import com.stonefive.chalkak.domain.model.photoedit.PhotoColorTransform
import com.stonefive.chalkak.domain.model.photoedit.PhotoEdit
import com.stonefive.chalkak.domain.model.photoedit.PhotoEditTransform
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AndroidPostImageEncoder(
    contentResolver: ContentResolver,
    private val cacheDir: File,
) : PostImageEncoder {
    private val decoder = OrientedBitmapDecoder(contentResolver)

    override suspend fun encode(
        contentUri: String,
        maxBytes: Long,
        edit: PhotoEdit,
    ): PostImageEncodeResult {
        var resultFile: File? = null
        return try {
            withContext(Dispatchers.IO) {
                val result = encodeBlocking(contentUri, maxBytes, edit)
                resultFile = (result as? PostImageEncodeResult.Success)?.file
                coroutineContext.ensureActive()
                result
            }
        } catch (error: CancellationException) {
            resultFile?.delete()
            throw error
        }
    }

    private fun encodeBlocking(
        contentUri: String,
        maxBytes: Long,
        edit: PhotoEdit,
    ): PostImageEncodeResult {
        if (maxBytes <= 0) return PostImageEncodeResult.SizeLimitExceeded

        val uri = runCatching { Uri.parse(contentUri) }.getOrNull()
            ?: return PostImageEncodeResult.UnreadableUri
        val orientedBitmap = when (val decoded = decoder.decode(uri, ::calculateInSampleSize)) {
            OrientedBitmapDecoder.Result.Unreadable -> return PostImageEncodeResult.UnreadableUri
            OrientedBitmapDecoder.Result.Failed -> return PostImageEncodeResult.DecodeFailed
            is OrientedBitmapDecoder.Result.Success -> decoded.bitmap
        }

        val editedBitmap = try {
            if (edit.isIdentity) orientedBitmap else applyEdit(orientedBitmap, edit)
        } catch (error: CancellationException) {
            orientedBitmap.recycle()
            throw error
        } catch (_: RuntimeException) {
            orientedBitmap.recycle()
            return PostImageEncodeResult.EncodeFailed
        }
        if (editedBitmap !== orientedBitmap) {
            orientedBitmap.recycle()
        }

        val cacheReady = try {
            cacheDir.isDirectory || (cacheDir.mkdirs() && cacheDir.isDirectory)
        } catch (_: SecurityException) {
            false
        }
        if (!cacheReady) {
            if (!editedBitmap.isRecycled) editedBitmap.recycle()
            return PostImageEncodeResult.EncodeFailed
        }

        var candidate = editedBitmap
        var activeOutput: File? = null
        try {
            repeat(MAX_RESCALE_ROUNDS + 1) { round ->
                for (quality in QUALITY_LADDER) {
                    val output = try {
                        File.createTempFile(OUTPUT_PREFIX, OUTPUT_SUFFIX, cacheDir)
                    } catch (_: IOException) {
                        return PostImageEncodeResult.EncodeFailed
                    }
                    activeOutput = output

                    when (compress(candidate, quality, output)) {
                        CompressionResult.Failed -> {
                            output.delete()
                            activeOutput = null
                            return PostImageEncodeResult.EncodeFailed
                        }

                        CompressionResult.Empty -> {
                            output.delete()
                            activeOutput = null
                            return PostImageEncodeResult.EncodeFailed
                        }

                        CompressionResult.Completed -> {
                            if (output.length() in 1..maxBytes) {
                                activeOutput = null
                                return PostImageEncodeResult.Success(output)
                            }
                            output.delete()
                            activeOutput = null
                        }
                    }
                }

                if (round == MAX_RESCALE_ROUNDS) {
                    return PostImageEncodeResult.SizeLimitExceeded
                }

                val resized = resize(candidate) ?: return PostImageEncodeResult.SizeLimitExceeded
                if (resized === candidate) {
                    return PostImageEncodeResult.SizeLimitExceeded
                }
                candidate.recycle()
                candidate = resized
            }
        } catch (error: CancellationException) {
            activeOutput?.delete()
            throw error
        } catch (_: RuntimeException) {
            return PostImageEncodeResult.EncodeFailed
        } finally {
            activeOutput?.delete()
            candidate.recycle()
            if (candidate !== editedBitmap && !editedBitmap.isRecycled) {
                editedBitmap.recycle()
            }
        }

        return PostImageEncodeResult.SizeLimitExceeded
    }

    // 조정이 없으면 필터 색을 그리면서 함께 입히고, 조정이 있으면 그린 뒤 픽셀마다 조정과 필터를 적용한다.
    private fun applyEdit(
        bitmap: Bitmap,
        edit: PhotoEdit,
    ): Bitmap {
        val transform = PhotoEditTransform(edit, bitmap.width, bitmap.height)
        val matrix = Matrix().apply { setValues(transform.matrixValues()) }
        val hasAdjustments = !edit.adjustments.isIdentity
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            if (!hasAdjustments) {
                colorFilter = edit.filter
                    .colorMatrix()
                    ?.let(::ColorMatrixColorFilter)
            }
        }
        val output = Bitmap.createBitmap(transform.outputWidth, transform.outputHeight, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(bitmap, matrix, paint)
        if (hasAdjustments) output.applyColorTransform(PhotoColorTransform.of(edit))
        return output
    }

    // 큰 사진도 픽셀 배열을 한 번에 만들지 않도록 몇 줄씩 나눠 처리한다.
    private fun Bitmap.applyColorTransform(transform: PhotoColorTransform) {
        val rowsPerBand = max(1, TRANSFORM_BAND_PIXELS / width)
        val band = IntArray(width * rowsPerBand)
        var top = 0
        while (top < height) {
            val rows = minOf(rowsPerBand, height - top)
            getPixels(band, 0, width, 0, top, width, rows)
            transform.apply(band, length = width * rows)
            setPixels(band, 0, width, 0, top, width, rows)
            top += rows
        }
    }

    private fun resize(bitmap: Bitmap): Bitmap? {
        val width = max(1, (bitmap.width * RESCALE_FACTOR).roundToInt())
        val height = max(1, (bitmap.height * RESCALE_FACTOR).roundToInt())
        if (width == bitmap.width && height == bitmap.height) return null
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun compress(
        bitmap: Bitmap,
        quality: Int,
        output: File,
    ): CompressionResult = try {
        val compressed = FileOutputStream(output).use { stream ->
            bitmap.compress(webpFormat(), quality, stream)
        }
        when {
            !compressed -> CompressionResult.Failed
            output.length() <= 0 -> CompressionResult.Empty
            else -> CompressionResult.Completed
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: IOException) {
        CompressionResult.Failed
    } catch (_: RuntimeException) {
        CompressionResult.Failed
    }

    private fun webpFormat(): Bitmap.CompressFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Bitmap.CompressFormat.WEBP_LOSSY
    } else {
        @Suppress("DEPRECATION")
        Bitmap.CompressFormat.WEBP
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
    ): Int {
        val longEdge = max(width, height).toLong()
        var sampleSize = 1L
        while (longEdge / sampleSize > INITIAL_MAX_LONG_EDGE) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private enum class CompressionResult {
        Completed,
        Empty,
        Failed,
    }

    private companion object {
        const val INITIAL_MAX_LONG_EDGE = 4096
        const val RESCALE_FACTOR = 0.85f
        const val MAX_RESCALE_ROUNDS = 3
        const val TRANSFORM_BAND_PIXELS = 1 shl 20
        const val OUTPUT_PREFIX = "post-upload-"
        const val OUTPUT_SUFFIX = ".webp"
        val QUALITY_LADDER = intArrayOf(90, 80, 60, 40)
    }
}
