package com.stonefive.chalkak.domain.model.photoedit

import kotlin.math.PI
import kotlin.math.sin

/**
 * 블랙 포인트·그림자·하이라이트·대비를 "값 → 새 값" 표 하나로 합친다. 값은 0..255 저장 값을 0..1로 나눈 것이다.
 * [PhotoColorTransform]이 R·G·B 각 채널을 따로 이 표로 바꾼다. 픽셀 밝기가 아니라 채널마다 바꾸므로,
 * 대비를 올리면 색도 조금 진해지는 식으로 색감이 함께 바뀐다.
 *
 * 표를 만드는 순서:
 * 1. 블랙 포인트: 이 밝기 아래는 검게 만들고, 나머지를 다시 0..1로 편다. 음수면 반대로 검은색을 회색 쪽으로 띄운다.
 * 2. 그림자·하이라이트: 어두운 쪽(1/3 부근)·밝은 쪽(2/3 부근)만 올리거나 내린다. 0과 1은 움직이지 않는다.
 * 3. 대비: 0.5를 기준으로 어두운 쪽은 더 어둡게, 밝은 쪽은 더 밝게(S자) 벌린다.
 */
object PhotoToneCurve {
    const val TABLE_SIZE = 256

    private const val TONE_AMOUNT = 0.25
    private const val CONTRAST_AMOUNT = 0.6

    fun isIdentity(adjustments: PhotoAdjustments): Boolean = adjustments.blackPoint == 0f &&
        adjustments.shadows == 0f &&
        adjustments.highlights == 0f &&
        adjustments.contrast == 0f

    /**
     * 입력 밝기 0..1을 [TABLE_SIZE]칸으로 나눈 칸마다의 출력 밝기. 입력이 밝을수록 출력도 같거나 밝다(뒤집히지 않는다).
     * 예: 모든 조정이 0이면 i번째 값은 i / 255다.
     */
    fun lookupTable(adjustments: PhotoAdjustments): DoubleArray {
        val blackPoint = adjustments.blackPoint.toDouble()
        val inputBlack = PhotoAdjustmentScale.inputBlack(blackPoint)
        val outputBlack = PhotoAdjustmentScale.outputBlack(blackPoint)
        val shadows = adjustments.shadows.toDouble()
        val highlights = adjustments.highlights.toDouble()
        val contrast = adjustments.contrast.toDouble()

        var previous = 0.0
        return DoubleArray(TABLE_SIZE) { index ->
            var t = PhotoAdjustmentScale.stretchAboveBlack(index.toDouble() / (TABLE_SIZE - 1), inputBlack)
            // t(1−t)² 는 t = 1/3에서, t²(1−t) 는 t = 2/3에서 가장 크고 0과 1에서는 0이다.
            t += shadows * TONE_AMOUNT * 4 * t * (1 - t) * (1 - t)
            t += highlights * TONE_AMOUNT * 4 * t * t * (1 - t)
            // sin(2πt)는 0.5보다 어두우면 양수, 밝으면 음수라서 빼면 양쪽으로 벌어진다.
            t -= contrast * CONTRAST_AMOUNT * sin(2 * PI * t) / (2 * PI)
            val y = outputBlack + (1 - outputBlack) * t.coerceIn(0.0, 1.0)
            previous = maxOf(previous, y)
            previous
        }
    }
}
