package com.stonefive.chalkak.data.post

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AndroidPostImageEncoder(
    private val contentResolver: ContentResolver,
    private val cacheDir: File,
) : PostImageEncoder {
    override suspend fun encode(
        contentUri: String,
        maxBytes: Long,
    ): PostImageEncodeResult {
        var resultFile: File? = null
        return try {
            withContext(Dispatchers.IO) {
                val result = encodeBlocking(contentUri, maxBytes)
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
    ): PostImageEncodeResult {
        if (maxBytes <= 0) return PostImageEncodeResult.SizeLimitExceeded

        val uri = runCatching { Uri.parse(contentUri) }.getOrNull()
            ?: return PostImageEncodeResult.UnreadableUri
        val bounds = try {
            readBounds(uri)
        } catch (error: CancellationException) {
            throw error
        } catch (_: RuntimeException) {
            return PostImageEncodeResult.DecodeFailed
        } ?: return PostImageEncodeResult.UnreadableUri
        if (bounds.width <= 0 || bounds.height <= 0) {
            return PostImageEncodeResult.DecodeFailed
        }

        val sampledBitmap = try {
            decode(uri, bounds)
        } catch (error: CancellationException) {
            throw error
        } catch (_: RuntimeException) {
            null
        } ?: return PostImageEncodeResult.DecodeFailed
        val orientation = readOrientation(uri) ?: run {
            sampledBitmap.recycle()
            return PostImageEncodeResult.DecodeFailed
        }
        val orientedBitmap = try {
            applyOrientation(sampledBitmap, orientation)
        } catch (error: CancellationException) {
            sampledBitmap.recycle()
            throw error
        } catch (_: RuntimeException) {
            sampledBitmap.recycle()
            return PostImageEncodeResult.DecodeFailed
        }
        if (orientedBitmap !== sampledBitmap) {
            sampledBitmap.recycle()
        }

        val cacheReady = try {
            cacheDir.isDirectory || (cacheDir.mkdirs() && cacheDir.isDirectory)
        } catch (_: SecurityException) {
            false
        }
        if (!cacheReady) {
            if (!orientedBitmap.isRecycled) orientedBitmap.recycle()
            return PostImageEncodeResult.EncodeFailed
        }

        var candidate = orientedBitmap
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
            if (candidate !== orientedBitmap && !orientedBitmap.isRecycled) {
                orientedBitmap.recycle()
            }
        }

        return PostImageEncodeResult.SizeLimitExceeded
    }

    private fun readBounds(uri: Uri): ImageBounds? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val opened = openInputStream(uri) ?: return null
        opened.use { input -> BitmapFactory.decodeStream(input, null, options) }
        return ImageBounds(options.outWidth, options.outHeight)
    }

    private fun decode(
        uri: Uri,
        bounds: ImageBounds,
    ): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.width, bounds.height)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
            inScaled = false
        }
        val input = openInputStream(uri) ?: return null
        return input.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun readOrientation(uri: Uri): Int? {
        val input = openInputStream(uri) ?: return null
        return try {
            input.use {
                ExifInterface(
                    it,
                ).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun applyOrientation(
        bitmap: Bitmap,
        orientation: Int,
    ): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)

            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)

            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)

            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
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

    private fun openInputStream(uri: Uri): InputStream? = try {
        contentResolver.openInputStream(uri)
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: RuntimeException) {
        null
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

    private data class ImageBounds(
        val width: Int,
        val height: Int,
    )

    private enum class CompressionResult {
        Completed,
        Empty,
        Failed,
    }

    private companion object {
        const val INITIAL_MAX_LONG_EDGE = 4096
        const val RESCALE_FACTOR = 0.85f
        const val MAX_RESCALE_ROUNDS = 3
        const val OUTPUT_PREFIX = "post-upload-"
        const val OUTPUT_SUFFIX = ".webp"
        val QUALITY_LADDER = intArrayOf(90, 85, 80, 75, 70)
    }
}
