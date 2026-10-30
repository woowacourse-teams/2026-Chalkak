package com.stonefive.chalkak.core.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.scale
import com.stonefive.chalkak.domain.model.PhotoColor

private const val SAMPLE_SIZE = 8
private const val SATURATION_BOOST = 1.2f

/** 사진을 아주 작게 줄여 평균색을 구하고, 칸에서 너무 탁해 보이지 않게 채도를 조금 올린다. */
fun Bitmap.photoColor(): PhotoColor? {
    if (width <= 0 || height <= 0) return null
    val sample = scale(SAMPLE_SIZE, SAMPLE_SIZE)
    val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
    sample.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
    if (sample !== this) sample.recycle()

    var red = 0f
    var green = 0f
    var blue = 0f
    var weight = 0f
    for (pixel in pixels) {
        val alpha = Color.alpha(pixel) / 255f
        if (alpha <= 0f) continue
        // 투명 영역은 빼고 알파만큼 무게를 두어 평균한다.
        red += Color.red(pixel) / 255f * alpha
        green += Color.green(pixel) / 255f * alpha
        blue += Color.blue(pixel) / 255f * alpha
        weight += alpha
    }
    if (weight <= 0f) return null

    val hsv = FloatArray(3)
    Color.RGBToHSV(
        (red / weight * 255).toInt(),
        (green / weight * 255).toInt(),
        (blue / weight * 255).toInt(),
        hsv,
    )
    hsv[1] = minOf(1f, hsv[1] * SATURATION_BOOST)
    val boosted = Color.HSVToColor(hsv)
    return PhotoColor(
        red = Color.red(boosted) / 255f,
        green = Color.green(boosted) / 255f,
        blue = Color.blue(boosted) / 255f,
    )
}
