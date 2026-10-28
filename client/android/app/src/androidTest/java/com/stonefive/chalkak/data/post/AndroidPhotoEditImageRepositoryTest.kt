package com.stonefive.chalkak.data.post

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPhotoEditImageRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = AndroidPhotoEditImageRepository(context.contentResolver)

    @Test
    fun loadsOrientedPixelsKeepingRequestedLongEdge() = runBlocking {
        val source = createJpeg(width = 400, height = 200, orientation = ExifInterface.ORIENTATION_ROTATE_90)
        try {
            val pixels = repository.loadPixels(Uri.fromFile(source).toString(), minLongEdge = 100)

            // 긴 변 400을 100 이상으로 남기는 가장 큰 배율(4배)로 줄이고, EXIF 회전으로 세로가 길어진다.
            assertNotNull(pixels)
            assertEquals(50, pixels!!.width)
            assertEquals(100, pixels.height)
            assertTrue(pixels.argb.all { Color.red(it) > RED_MIN && Color.blue(it) < BLUE_MAX })
        } finally {
            source.delete()
        }
    }

    @Test
    fun returnsNullWhenImageCannotBeRead() = runBlocking {
        assertNull(repository.loadPixels(Uri.fromFile(File(context.cacheDir, "missing.jpg")).toString(), 100))
    }

    private fun createJpeg(
        width: Int,
        height: Int,
        orientation: Int,
    ): File {
        val file = File.createTempFile("photo-edit-source-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        try {
            FileOutputStream(file).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file
    }

    private companion object {
        const val RED_MIN = 200
        const val BLUE_MAX = 60
    }
}
