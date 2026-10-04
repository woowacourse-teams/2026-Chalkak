package com.stonefive.chalkak.feature.upload

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

data class CameraCapture(
    val file: File,
    val uri: Uri,
)

fun createCameraCapture(context: Context): CameraCapture {
    val file = File(
        context.cacheDir,
        "photo-upload/camera/${UUID.randomUUID()}.jpg",
    ).apply {
        parentFile?.mkdirs()
    }

    return CameraCapture(
        file = file,
        uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        ),
    )
}

fun shouldRequestLegacyGalleryPermission(
    sdkInt: Int,
    permissionStatus: Int,
): Boolean = sdkInt <= Build.VERSION_CODES.P &&
    permissionStatus != PackageManager.PERMISSION_GRANTED

fun shouldRequestLegacyGalleryPermission(context: Context): Boolean = shouldRequestLegacyGalleryPermission(
    sdkInt = Build.VERSION.SDK_INT,
    permissionStatus = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    ),
)

fun saveCameraCaptureToGallery(
    context: Context,
    file: File,
): Boolean {
    if (!file.exists()) return false

    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, cameraCaptureGalleryDisplayName(file.name))
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/Chalkak",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val galleryUri = runCatching {
        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }.getOrNull() ?: return false

    return runCatching {
        file.inputStream().use { inputStream ->
            resolver.openOutputStream(galleryUri)?.use { outputStream ->
                inputStream.copyTo(outputStream)
            } ?: error("이미지 저장 스트림을 열 수 없습니다")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val updatedRows = resolver.update(
                galleryUri,
                ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                },
                null,
                null,
            )
            check(updatedRows > 0)
        }
        true
    }.getOrElse {
        runCatching { resolver.delete(galleryUri, null, null) }
        false
    }
}

internal fun cameraCaptureGalleryDisplayName(fileName: String): String =
    "chalkak-photo-${fileName.substringBeforeLast(".")}.jpg"
