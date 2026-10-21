package com.stonefive.chalkak.feature.photoedit

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.stonefive.chalkak.domain.model.photoedit.PhotoAdjustments
import com.stonefive.chalkak.domain.model.photoedit.PhotoColorTransform
import com.stonefive.chalkak.domain.model.photoedit.PhotoFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun PhotoFilter.toColorFilter(): ColorFilter? = colorMatrix()?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }

/**
 * 편집 화면 미리보기에 쓸, 조정을 적용한 사진. 사진을 긴 변 1440px로 불러와 업로드와 같은
 * [PhotoColorTransform]으로 픽셀마다 바꾼다. 필터는 여기서 적용하지 않고 그릴 때 [toColorFilter]로 입힌다.
 *
 * 조정이 없거나 아직 만들지 못했으면 null이고, 이때는 원본을 그리면 된다.
 * 눈금자를 끄는 동안 값이 빠르게 바뀌면, 만들던 이미지를 끝낸 뒤 가장 최근 값으로 다시 만든다.
 */
@Composable
fun rememberAdjustedImage(
    model: Any?,
    adjustments: PhotoAdjustments,
): ImageBitmap? {
    val context = LocalContext.current
    val source by produceState<Bitmap?>(initialValue = null, model) {
        val request = ImageRequest
            .Builder(context)
            .data(model)
            .size(PREVIEW_LONG_EDGE)
            .allowHardware(false)
            .build()
        value = (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
    }
    val currentAdjustments by rememberUpdatedState(adjustments)
    var adjusted by remember(model) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(source) {
        val bitmap = source ?: return@LaunchedEffect
        snapshotFlow { currentAdjustments }.collect { target ->
            adjusted = if (target.isIdentity) {
                null
            } else {
                withContext(Dispatchers.Default) { bitmap.adjusted(target) }
            }
        }
    }
    // 조정을 모두 되돌린 직후에도 이전 결과를 보여 주지 않도록 지금 값이 조정 없음이면 바로 원본을 쓰게 한다.
    return adjusted?.takeUnless { adjustments.isIdentity }
}

private fun Bitmap.adjusted(adjustments: PhotoAdjustments): ImageBitmap {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    PhotoColorTransform(adjustments).apply(pixels)
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

// 편집 화면을 채우기에 충분하면서 끄는 동안 다시 계산할 수 있는 크기.
private const val PREVIEW_LONG_EDGE = 1440
