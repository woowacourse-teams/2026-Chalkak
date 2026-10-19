package com.stonefive.chalkak.domain.model.photoedit

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoEditTransformTest {
    @Test
    fun `결과 좌표를 원본으로 되돌리면 정방향 행렬로 옮긴 점과 맞는다`() {
        for (quarterTurns in 0..3) {
            for (straightenDegrees in listOf(0f, 13f, -13f)) {
                val edit = PhotoEdit(
                    quarterTurns = quarterTurns,
                    straightenDegrees = straightenDegrees,
                    crop = CropWindow(centerX = 0.4f, centerY = 0.55f, width = 0.5f, height = 0.6f),
                )
                val transform = PhotoEditTransform(edit, sourceWidth = 400, sourceHeight = 300)
                val matrix = transform.sourceToOutputMatrix()

                for ((sourceX, sourceY) in listOf(0f to 0f, 160f to 165f, 399f to 120f, 250f to 299f)) {
                    val outputX = matrix[0] * sourceX + matrix[1] * sourceY + matrix[2]
                    val outputY = matrix[3] * sourceX + matrix[4] * sourceY + matrix[5]

                    assertEquals(sourceX, transform.sourceX(outputX, outputY), TOLERANCE)
                    assertEquals(sourceY, transform.sourceY(outputX, outputY), TOLERANCE)
                }
            }
        }
    }

    @Test
    fun `자르기 중심은 결과 가운데로 온다`() {
        val edit = PhotoEdit(
            quarterTurns = 1,
            straightenDegrees = 7f,
            crop = CropWindow(centerX = 0.3f, centerY = 0.7f, width = 0.4f, height = 0.4f),
        )
        val transform = PhotoEditTransform(edit, sourceWidth = 400, sourceHeight = 300)

        assertEquals(120f, transform.sourceX(transform.outputWidth / 2f, transform.outputHeight / 2f), TOLERANCE)
        assertEquals(210f, transform.sourceY(transform.outputWidth / 2f, transform.outputHeight / 2f), TOLERANCE)
    }

    @Test
    fun `주석과 문서의 예시대로 돌린 사진의 왼쪽 절반은 원본의 아래쪽 절반이다`() {
        val edit = PhotoEdit(
            quarterTurns = 1,
            crop = CropWindow(centerX = 0.5f, centerY = 0.75f, width = 0.5f, height = 1f),
        )
        val transform = PhotoEditTransform(edit, sourceWidth = 4000, sourceHeight = 3000)

        assertEquals(1500 to 4000, transform.outputWidth to transform.outputHeight)
        assertEquals(2000f, transform.sourceX(750f, 2000f), TOLERANCE)
        assertEquals(2250f, transform.sourceY(750f, 2000f), TOLERANCE)
        assertEquals(0f, transform.sourceX(0f, 0f), TOLERANCE)
        assertEquals(3000f, transform.sourceY(0f, 0f), TOLERANCE)
    }

    @Test
    fun `결과 크기는 90도 회전한 원본에 대한 비율이고 1픽셀보다 작아지지 않는다`() {
        val crop = CropWindow(centerX = 0.5f, centerY = 0.5f, width = 0.5f, height = 1f)

        val upright = PhotoEditTransform(PhotoEdit(crop = crop), sourceWidth = 400, sourceHeight = 300)
        val turned = PhotoEditTransform(PhotoEdit(quarterTurns = 1, crop = crop), sourceWidth = 400, sourceHeight = 300)
        val tiny = PhotoEditTransform(
            PhotoEdit(crop = CropWindow(centerX = 0.5f, centerY = 0.5f, width = 0.001f, height = 0.001f)),
            sourceWidth = 400,
            sourceHeight = 300,
        )

        assertEquals(200 to 300, upright.outputWidth to upright.outputHeight)
        assertEquals(150 to 400, turned.outputWidth to turned.outputHeight)
        assertEquals(1 to 1, tiny.outputWidth to tiny.outputHeight)
    }

    @Test
    fun `90도 단위로 돌린 픽셀은 정확히 자리를 바꾼다`() {
        // 0 1
        // 2 3
        // 4 5
        val source = PhotoPixels(width = 2, height = 3, argb = IntArray(6) { it })

        assertEquals(
            listOf(4, 2, 0, 5, 3, 1),
            source
                .sampled(PhotoEdit(quarterTurns = 1), 256)
                .argb
                .toList(),
        )
        assertEquals(
            listOf(5, 4, 3, 2, 1, 0),
            source
                .sampled(PhotoEdit(quarterTurns = 2), 256)
                .argb
                .toList(),
        )
        assertEquals(
            listOf(1, 3, 5, 0, 2, 4),
            source
                .sampled(PhotoEdit(quarterTurns = 3), 256)
                .argb
                .toList(),
        )
    }

    private companion object {
        const val TOLERANCE = 1e-3f
    }
}
