package com.stonefive.chalkak.feature.record

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.time.YearMonth

fun saveCalendarImageToGallery(
    context: Context,
    image: ImageBitmap,
    month: YearMonth,
): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "chalkak-calendar-$month.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/Chalkak",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = runCatching {
        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }.getOrNull() ?: return false

    return runCatching {
        resolver.openOutputStream(uri)?.use { outputStream ->
            check(
                image.asAndroidBitmap().compress(
                    Bitmap.CompressFormat.PNG,
                    100,
                    outputStream,
                ),
            )
        } ?: error("이미지 저장 스트림을 열 수 없습니다")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                },
                null,
                null,
            )
        }
        true
    }.getOrElse {
        resolver.delete(uri, null, null)
        false
    }
}
