package com.stonefive.chalkak.domain.model.photoedit

/**
 * 업로드 전에 사진에 적용할 편집 값.
 *
 * 모든 좌표는 해상도와 무관한 비율로 둔다. 화면 미리보기, 다운샘플한 인코더 입력, URL 만료 후 재인코딩이
 * 서로 다른 해상도를 쓰더라도 같은 값으로 같은 결과를 만든다.
 */
data class PhotoEdit(
    val quarterTurns: Int = 0,
    val straightenDegrees: Float = 0f,
    val crop: CropWindow = CropWindow.Full,
    val filter: PhotoFilter = PhotoFilter.Original,
    val adjustmentLayers: PhotoAdjustmentLayers = PhotoAdjustmentLayers(),
) {
    val adjustments: PhotoAdjustments get() = adjustmentLayers.applied

    val isGeometryIdentity: Boolean
        get() = quarterTurns == 0 && straightenDegrees == 0f && crop == CropWindow.Full

    val isIdentity: Boolean
        get() = isGeometryIdentity && filter == PhotoFilter.Original && adjustments.isIdentity

    val rotationDegrees: Float get() = quarterTurns * QUARTER_TURN_DEGREES + straightenDegrees

    companion object {
        const val QUARTER_TURN_DEGREES = 90f
        const val MAX_STRAIGHTEN_DEGREES = 45f
    }
}

/**
 * 자르기 영역.
 *
 * - [centerX], [centerY]: 잘라낸 영역의 중심. EXIF 방향을 보정한 원본의 가로·세로에 대한 0..1 비율이다.
 * - [width], [height]: 결과 이미지의 가로·세로. 90° 단위 회전을 적용한 원본의 가로·세로에 대한 비율이다.
 *
 * 영역은 결과 이미지 기준으로 축에 정렬되어 있고, 원본은 [PhotoEdit.rotationDegrees]만큼 시계 방향으로 돌아간다.
 */
data class CropWindow(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
) {
    companion object {
        val Full = CropWindow(centerX = 0.5f, centerY = 0.5f, width = 1f, height = 1f)
    }
}

/**
 * 필터 색 변환 계수. 4×5 행 우선 색 행렬이며 마지막 열은 0..255 범위의 더하는 값이다.
 * 조정이 없으면 data에서는 `android.graphics.ColorMatrix`, 미리보기에서는 Compose `ColorMatrix`가 같은 계수를 쓰고,
 * 조정이 있으면 [PhotoColorTransform]이 조정 뒤에 같은 계수를 곱한다.
 */
enum class PhotoFilter(private val matrix: FloatArray?) {
    Original(null),
    Mono(
        floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
    Warm(
        floatArrayOf(
            1.08f, 0f, 0f, 0f, 8f,
            0f, 1.02f, 0f, 0f, 4f,
            0f, 0f, 0.88f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
    Cool(
        floatArrayOf(
            0.9f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 2f,
            0f, 0f, 1.1f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
    Vintage(
        floatArrayOf(
            0.63f, 0.35f, 0.09f, 0f, 18f,
            0.16f, 0.76f, 0.08f, 0f, 14f,
            0.12f, 0.24f, 0.51f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
    Vivid(
        floatArrayOf(
            1.2754f, -0.2503f, -0.0252f, 0f, 0f,
            -0.0746f, 1.0997f, -0.0252f, 0f, 0f,
            -0.0746f, -0.2503f, 1.3248f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
    ;

    /** 호출부가 계수를 바꿔도 다른 곳에 영향이 없도록 복사본을 돌려준다. 원본 필터는 `null`이다. */
    fun colorMatrix(): FloatArray? = matrix?.copyOf()
}
