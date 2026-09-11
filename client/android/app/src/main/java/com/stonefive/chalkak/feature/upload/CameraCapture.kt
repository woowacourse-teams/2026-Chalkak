package com.stonefive.chalkak.feature.upload

import android.content.Context
import android.net.Uri
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
