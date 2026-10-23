package com.stonefive.chalkak.domain.model.photoedit

/**
 * 사용자가 편집 화면에서 정한 값 전체. 편집 화면이 만들고, 업로드 인코더가 이 값대로 원본을 다시 그려 올린다.
 *
 * 픽셀 수가 아니라 사진 크기에 대한 비율로 저장한다. 미리보기는 화면 크기로, 인코더는 긴 변 4096 이하로 줄인
 * 사진을 쓰는데, 비율이라서 어느 해상도에 적용해도 같은 영역·같은 색이 나온다.
 * 좌표 기준과 예시는 docs/architecture/photo-edit.md 참고.
 */
data class PhotoEdit(
    /** 시계 방향으로 90°씩 돌린 횟수(0..3). */
    val quarterTurns: Int = 0,
    /** 90° 회전과 별개로 더 돌린 각도. 지금 화면에서는 쓰지 않아 항상 0이다. */
    val straightenDegrees: Float = 0f,
    val crop: CropWindow = CropWindow.Full,
    val filter: PhotoFilter = PhotoFilter.Original,
    val adjustmentLayers: PhotoAdjustmentLayers = PhotoAdjustmentLayers(),
) {
    /** 사진에 실제로 적용할 조정 값. 자동 보정과 사용자 값을 합친 결과다. */
    val adjustments: PhotoAdjustments get() = adjustmentLayers.applied

    val isGeometryIdentity: Boolean
        get() = quarterTurns == 0 && straightenDegrees == 0f && crop == CropWindow.Full

    /** true면 업로드할 때 원본을 다시 그리지 않고 그대로 인코딩한다. */
    val isIdentity: Boolean
        get() = isGeometryIdentity && filter == PhotoFilter.Original && adjustments.isIdentity

    val rotationDegrees: Float get() = quarterTurns * QUARTER_TURN_DEGREES + straightenDegrees

    companion object {
        const val QUARTER_TURN_DEGREES = 90f
        const val MAX_STRAIGHTEN_DEGREES = 45f
    }
}

/**
 * 남길 영역. 중심과 크기의 기준이 서로 다르니 주의한다.
 *
 * - [centerX], [centerY]: 돌리기 **전** 원본(source)의 가로·세로에 대한 0..1 비율.
 * - [width], [height]: 90°씩 돌린 **뒤** 원본(turned)의 가로·세로에 대한 0..1 비율.
 *   결과 사진의 픽셀 크기는 `width × 돌린 원본 가로`, `height × 돌린 원본 세로`다.
 *
 * 예: 4000×3000 사진을 한 번 돌리면(quarterTurns = 1) 돌린 원본은 3000×4000이다.
 * 돌린 사진의 **왼쪽** 절반만 남기면 결과는 1500×4000이고 `CropWindow(0.5f, 0.75f, 0.5f, 1f)`다.
 * 크기(0.5, 1)는 돌린 사진 기준이라 바로 보이지만, 중심은 돌리기 전 원본 기준이라 (0.25, 0.5)가 아니다.
 * 돌린 사진의 왼쪽은 돌리기 전 원본의 아래쪽이므로 중심은 원본 (2000, 2250), 비율로 (0.5, 0.75)다.
 * 변환식은 docs/architecture/photo-edit.md의 "좌표계" 참고.
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
 * 필터마다 R·G·B·A를 섞는 비율. 결과 R = m[0]·R + m[1]·G + m[2]·B + m[3]·A + m[4] 처럼 한 줄에 5개씩, 4줄이다.
 * 색 값은 0..255이고 다섯째 값은 그 범위에서 더하는 값이다. Android `ColorMatrix`가 같은 배치를 쓴다.
 */
enum class PhotoFilter(private val matrix: FloatArray?) {
    Original(null),

    // 저장 값 R·G·B를 0.299·R + 0.587·G + 0.114·B(Rec. 601 밝기)로 섞어 회색으로 만든다.
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

    /** 원본(필터 없음)은 `null`이다. 호출부가 고쳐도 되도록 복사본을 돌려준다. */
    fun colorMatrix(): FloatArray? = matrix?.copyOf()
}
