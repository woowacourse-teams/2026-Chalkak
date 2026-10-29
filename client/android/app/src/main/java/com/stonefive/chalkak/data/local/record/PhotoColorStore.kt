package com.stonefive.chalkak.data.local.record

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.stonefive.chalkak.core.ui.photoColor
import com.stonefive.chalkak.domain.model.PhotoColor
import com.stonefive.chalkak.domain.repository.PhotoColorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.photoColorDataStore by preferencesDataStore(name = "record_photo_colors")

/** 저장된 색이 있으면 쓰고, 없으면 썸네일을 내려받아 색을 구한 뒤 저장한다. */
class PhotoColorStore(context: Context) : PhotoColorRepository {
    private val applicationContext = context.applicationContext
    private val dataStore = applicationContext.photoColorDataStore

    override suspend fun getPhotoColor(
        postId: String,
        imageUrl: String,
    ): PhotoColor? {
        val key = intPreferencesKey(postId)
        dataStore.data
            .first()[key]
            ?.let { return it.toPhotoColor() }

        val request = ImageRequest
            .Builder(applicationContext)
            .data(imageUrl)
            // 픽셀을 읽어야 하므로 하드웨어 비트맵을 쓰지 않는다.
            .allowHardware(false)
            .build()
        val bitmap = (applicationContext.imageLoader.execute(request) as? SuccessResult)
            ?.image
            ?.toBitmap()
            ?: return null
        val color = withContext(Dispatchers.Default) { bitmap.photoColor() } ?: return null
        dataStore.edit { preferences -> preferences[key] = color.toPackedRgb() }
        return color
    }
}

private fun PhotoColor.toPackedRgb(): Int =
    ((red * 255).toInt() shl 16) or ((green * 255).toInt() shl 8) or (blue * 255).toInt()

private fun Int.toPhotoColor(): PhotoColor = PhotoColor(
    red = (this shr 16 and 0xFF) / 255f,
    green = (this shr 8 and 0xFF) / 255f,
    blue = (this and 0xFF) / 255f,
)
