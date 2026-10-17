package com.stonefive.chalkak.data.post

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stonefive.chalkak.domain.model.CropWindow
import com.stonefive.chalkak.domain.model.PhotoEdit
import com.stonefive.chalkak.domain.model.PhotoFilter
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPostImageEncoderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun encodesActualWebpAndStripsExifMetadata() = runBlocking {
        val source = createJpeg(ExifInterface.ORIENTATION_ROTATE_90)
        try {
            val result = encoder().encode(source.toContentUri(), MAX_BYTES, PhotoEdit())
            val output = (result as PostImageEncodeResult.Success).file
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(output.absolutePath, options)

                assertEquals("image/webp", options.outMimeType)
                assertEquals(1, options.outWidth)
                assertEquals(2, options.outHeight)
                assertTrue(output.length() in 1..MAX_BYTES)

                val outputExif = ExifInterface(output)
                assertNull(outputExif.getAttribute(ExifInterface.TAG_MAKE))
                assertNull(outputExif.getAttribute(ExifInterface.TAG_DATETIME))
                assertNull(outputExif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            } finally {
                output.delete()
            }
        } finally {
            source.delete()
        }
    }

    @Test
    fun appliesAllExifOrientationModesToPixels() = runBlocking {
        ORIENTATIONS.forEach { orientation ->
            val source = createJpeg(orientation)
            try {
                val result = encoder().encode(source.toContentUri(), MAX_BYTES, PhotoEdit())
                val output = (result as PostImageEncodeResult.Success).file
                try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(output.absolutePath, options)
                    val shouldSwapDimensions = orientation in SWAPPED_DIMENSION_ORIENTATIONS
                    assertEquals(if (shouldSwapDimensions) 1 else 2, options.outWidth)
                    assertEquals(if (shouldSwapDimensions) 2 else 1, options.outHeight)
                } finally {
                    output.delete()
                }
            } finally {
                source.delete()
            }
        }
    }

    @Test
    fun returnsBoundedFailureWhenNoEncodedVariantFitsTheCap() = runBlocking {
        val source = createJpeg(ExifInterface.ORIENTATION_NORMAL)
        try {
            assertEquals(
                PostImageEncodeResult.SizeLimitExceeded,
                encoder().encode(source.toContentUri(), 1L, PhotoEdit()),
            )
        } finally {
            source.delete()
        }
    }

    @Test
    fun cropsSelectedRegion() = runBlocking {
        val source = createQuadrantJpeg()
        try {
            val output = encodeToBitmap(
                source,
                PhotoEdit(crop = CropWindow(centerX = 0.75f, centerY = 0.25f, width = 0.5f, height = 0.5f)),
            )
            try {
                assertEquals(QUADRANT_SIZE, output.width)
                assertEquals(QUADRANT_SIZE, output.height)
                assertColorNear(Color.GREEN, output.getPixel(QUADRANT_SIZE / 2, QUADRANT_SIZE / 2))
            } finally {
                output.recycle()
            }
        } finally {
            source.delete()
        }
    }

    @Test
    fun quarterTurnSwapsDimensionsAndRotatesClockwise() = runBlocking {
        val source = createJpeg(width = QUADRANT_SIZE * 2, height = QUADRANT_SIZE) { x, _ ->
            if (x < QUADRANT_SIZE) Color.RED else Color.BLUE
        }
        try {
            val output = encodeToBitmap(source, PhotoEdit(quarterTurns = 1))
            try {
                assertEquals(QUADRANT_SIZE, output.width)
                assertEquals(QUADRANT_SIZE * 2, output.height)
                assertColorNear(Color.RED, output.getPixel(QUADRANT_SIZE / 2, QUADRANT_SIZE / 2))
                assertColorNear(Color.BLUE, output.getPixel(QUADRANT_SIZE / 2, QUADRANT_SIZE * 3 / 2))
            } finally {
                output.recycle()
            }
        } finally {
            source.delete()
        }
    }

    @Test
    fun monoFilterProducesNeutralPixels() = runBlocking {
        val source = createQuadrantJpeg()
        try {
            val output = encodeToBitmap(source, PhotoEdit(filter = PhotoFilter.Mono))
            try {
                listOf(
                    QUADRANT_SIZE / 2 to QUADRANT_SIZE / 2,
                    QUADRANT_SIZE * 3 / 2 to QUADRANT_SIZE / 2,
                    QUADRANT_SIZE / 2 to QUADRANT_SIZE * 3 / 2,
                ).forEach { (x, y) ->
                    val pixel = output.getPixel(x, y)
                    assertTrue(abs(Color.red(pixel) - Color.green(pixel)) <= NEUTRAL_TOLERANCE)
                    assertTrue(abs(Color.green(pixel) - Color.blue(pixel)) <= NEUTRAL_TOLERANCE)
                }
            } finally {
                output.recycle()
            }
        } finally {
            source.delete()
        }
    }

    private suspend fun encodeToBitmap(
        source: File,
        edit: PhotoEdit,
    ): Bitmap {
        val result = encoder().encode(source.toContentUri(), MAX_BYTES, edit)
        val output = (result as PostImageEncodeResult.Success).file
        return try {
            BitmapFactory.decodeFile(output.absolutePath)
        } finally {
            output.delete()
        }
    }

    private fun createQuadrantJpeg(): File = createJpeg(width = QUADRANT_SIZE * 2, height = QUADRANT_SIZE * 2) { x, y ->
        when {
            x < QUADRANT_SIZE && y < QUADRANT_SIZE -> Color.RED
            y < QUADRANT_SIZE -> Color.GREEN
            x < QUADRANT_SIZE -> Color.BLUE
            else -> Color.WHITE
        }
    }

    private fun createJpeg(
        width: Int,
        height: Int,
        colorAt: (Int, Int) -> Int,
    ): File {
        val file = File.createTempFile("post-encoder-edit-source-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    bitmap.setPixel(x, y, colorAt(x, y))
                }
            }
            FileOutputStream(file).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    private fun assertColorNear(
        expected: Int,
        actual: Int,
    ) {
        val channels = listOf(Color::red, Color::green, Color::blue)
        channels.forEach { channel ->
            assertTrue(
                "expected ${Integer.toHexString(expected)} but was ${Integer.toHexString(actual)}",
                abs(channel(expected) - channel(actual)) <= COLOR_TOLERANCE,
            )
        }
    }

    private fun encoder() = AndroidPostImageEncoder(
        contentResolver = context.contentResolver,
        cacheDir = File(context.cacheDir, ENCODER_CACHE_DIRECTORY),
    )

    private fun createJpeg(orientation: Int): File {
        val file = File.createTempFile("post-encoder-source-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, 0xFFFF0000.toInt())
            setPixel(1, 0, 0xFF0000FF.toInt())
        }
        try {
            FileOutputStream(file).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }

        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            setAttribute(ExifInterface.TAG_MAKE, "Test Camera")
            setAttribute(ExifInterface.TAG_DATETIME, "2026:08:29 12:00:00")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "37/1,30/1,0/1")
            saveAttributes()
        }
        return file
    }

    private fun File.toContentUri(): String = android.net.Uri
        .fromFile(this)
        .toString()

    private companion object {
        const val ENCODER_CACHE_DIRECTORY = "post-encoder-test"
        const val MAX_BYTES = 5_242_880L
        const val QUADRANT_SIZE = 32
        const val COLOR_TOLERANCE = 40
        const val NEUTRAL_TOLERANCE = 6
        val ORIENTATIONS = intArrayOf(
            ExifInterface.ORIENTATION_NORMAL,
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
            ExifInterface.ORIENTATION_ROTATE_180,
            ExifInterface.ORIENTATION_FLIP_VERTICAL,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270,
        )
        val SWAPPED_DIMENSION_ORIENTATIONS = setOf(
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270,
        )
    }
}
