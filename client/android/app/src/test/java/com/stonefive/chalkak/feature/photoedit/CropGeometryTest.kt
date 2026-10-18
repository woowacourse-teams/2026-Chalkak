package com.stonefive.chalkak.feature.photoedit

import com.stonefive.chalkak.domain.model.CropWindow
import com.stonefive.chalkak.domain.model.PhotoEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CropGeometryTest {
    // 4:3 가로 사진. 긴 변을 1로 두면 가로 1, 세로 0.75다.
    private val geometry = CropGeometry(imageWidth = 4000f, imageHeight = 3000f)

    @Test
    fun `편집하지 않은 전체 영역은 이미지 안에 있다`() {
        assertTrue(geometry.isInside(PhotoEdit()))
        assertRect(CropRect(0f, 0f, 1f, 0.75f), geometry.outputRect(PhotoEdit()))
    }

    @Test
    fun `이미지 밖으로 나간 영역은 크기를 유지한 채 안쪽으로 옮긴다`() {
        val edit = PhotoEdit(crop = CropWindow(centerX = 0.9f, centerY = 0.5f, width = 0.5f, height = 0.5f))
        assertFalse(geometry.isInside(edit))

        val constrained = geometry.constrain(edit)

        assertCrop(CropWindow(0.75f, 0.5f, 0.5f, 0.5f), constrained)
        assertTrue(geometry.isInside(edit.copy(crop = constrained)))
    }

    @Test
    fun `수평 보정으로 이미지를 벗어나면 비율을 유지한 채 영역을 줄인다`() {
        val edit = PhotoEdit(straightenDegrees = 10f)

        val constrained = geometry.constrain(edit)
        val straightened = edit.copy(crop = constrained)

        assertTrue(geometry.isInside(straightened))
        assertTrue(constrained.width < 1f)
        assertEquals(constrained.width, constrained.height, TOLERANCE)
        val rect = geometry.outputRect(straightened)
        assertEquals(4f / 3f, rect.width / rect.height, TOLERANCE)
    }

    @Test
    fun `이동은 이미지 경계에서 멈추고 크기를 바꾸지 않는다`() {
        val edit = PhotoEdit(crop = CropWindow(0.5f, 0.5f, 0.5f, 0.5f))

        val moved = geometry.move(edit, deltaX = 10f, deltaY = -10f)

        assertCrop(CropWindow(0.75f, 0.25f, 0.5f, 0.5f), moved)
    }

    @Test
    fun `회전한 상태의 이동은 화면 방향 기준으로 움직인다`() {
        val edit = PhotoEdit(quarterTurns = 1, crop = CropWindow(0.5f, 0.5f, 0.5f, 0.5f))

        // 시계 방향 90° 회전 후 화면 오른쪽은 원본의 위쪽이다.
        val moved = geometry.move(edit, deltaX = 0.05f, deltaY = 0f)

        assertEquals(0.5f, moved.centerX, TOLERANCE)
        assertEquals(0.5f - 0.05f / 0.75f, moved.centerY, TOLERANCE)
    }

    @Test
    fun `모서리를 끌면 반대편 모서리를 고정한 채 크기를 바꾼다`() {
        val edit = PhotoEdit(crop = CropWindow(0.5f, 0.5f, 0.5f, 0.5f))

        val resized = geometry.resize(
            edit,
            CropCorner.BottomEnd,
            deltaX = -0.1f,
            deltaY = -0.075f,
            lockedAspectRatio = null,
        )

        val before = geometry.outputRect(edit)
        val after = geometry.outputRect(edit.copy(crop = resized))
        assertEquals(before.left, after.left, TOLERANCE)
        assertEquals(before.top, after.top, TOLERANCE)
        assertEquals(0.4f, resized.width, TOLERANCE)
        assertEquals(0.4f, resized.height, TOLERANCE)
    }

    @Test
    fun `자유 비율 모서리는 경계에 닿은 변만 멈추고 다른 변은 계속 늘어난다`() {
        val edit = PhotoEdit(crop = CropWindow(0.25f, 0.25f, 0.5f, 0.5f))

        val resized = geometry.resize(edit, CropCorner.BottomEnd, deltaX = 10f, deltaY = 10f, lockedAspectRatio = null)

        assertCrop(CropWindow(0.5f, 0.5f, 1f, 1f), resized)
    }

    @Test
    fun `비율을 고정하면 모서리를 끌어도 비율을 지키고 경계에서 멈춘다`() {
        val square = geometry.largestCrop(PhotoEdit(), aspectRatio = 1f)
        val edit = PhotoEdit(crop = square.copy(width = square.width / 2f, height = square.height / 2f))

        val resized = geometry.resize(edit, CropCorner.TopStart, deltaX = -10f, deltaY = 0f, lockedAspectRatio = 1f)

        val rect = geometry.outputRect(edit.copy(crop = resized))
        assertEquals(1f, rect.width / rect.height, TOLERANCE)
        assertTrue(geometry.isInside(edit.copy(crop = resized)))
        assertTrue(rect.width > geometry.outputRect(edit).width)
    }

    @Test
    fun `모서리를 반대편 너머로 끌어도 최소 크기를 지킨다`() {
        val edit = PhotoEdit(crop = CropWindow(0.5f, 0.5f, 0.5f, 0.5f))

        val resized = geometry.resize(
            edit,
            CropCorner.BottomEnd,
            deltaX = -10f,
            deltaY = -10f,
            lockedAspectRatio = null,
        )

        val rect = geometry.outputRect(edit.copy(crop = resized))
        assertEquals(0.075f, rect.width, TOLERANCE)
        assertEquals(0.075f, rect.height, TOLERANCE)
    }

    @Test
    fun `비율을 고르면 그 비율의 가장 큰 영역으로 바꾼다`() {
        val square = geometry.largestCrop(PhotoEdit(), aspectRatio = 1f)
        assertCrop(CropWindow(0.5f, 0.5f, 0.75f, 1f), square)

        val wide = geometry.largestCrop(PhotoEdit(), aspectRatio = 16f / 9f)
        val rect = geometry.outputRect(PhotoEdit(crop = wide))
        assertEquals(1f, rect.width, TOLERANCE)
        assertEquals(9f / 16f, rect.height, TOLERANCE)
    }

    @Test
    fun `원본 비율은 90도 회전을 반영한다`() {
        assertEquals(4f / 3f, geometry.turnedAspectRatio(0), TOLERANCE)
        assertEquals(3f / 4f, geometry.turnedAspectRatio(1), TOLERANCE)
        assertCrop(CropWindow.Full, geometry.largestCrop(PhotoEdit(quarterTurns = 1), aspectRatio = null))
    }

    @Test
    fun `90도 회전은 가로와 세로를 바꾸고 원본 기준 중심은 유지한다`() {
        val edit = PhotoEdit(crop = CropWindow(0.3f, 0.4f, 0.4f, 0.2f))

        val rotated = geometry.rotateQuarter(edit)

        assertEquals(1, rotated.quarterTurns)
        assertCrop(CropWindow(0.3f, 0.4f, 0.2f, 0.4f), rotated.crop)
        val before = geometry.outputRect(edit)
        val after = geometry.outputRect(rotated)
        assertEquals(before.width, after.height, TOLERANCE)
        assertEquals(before.height, after.width, TOLERANCE)
        assertTrue(geometry.isInside(rotated))
    }

    @Test
    fun `프레임을 유지한 회전은 결과 사각형 크기를 그대로 둔다`() {
        val edit = PhotoEdit(crop = CropWindow(0.5f, 0.5f, 0.4f, 0.4f))

        val rotated = geometry.rotateQuarterKeepingFrame(edit)

        assertEquals(1, rotated.quarterTurns)
        val before = geometry.outputRect(edit)
        val after = geometry.outputRect(rotated)
        assertEquals(before.width, after.width, TOLERANCE)
        assertEquals(before.height, after.height, TOLERANCE)
        assertTrue(geometry.isInside(rotated))
    }

    @Test
    fun `프레임을 유지한 회전에서 사진을 벗어나면 비율을 지키며 줄인다`() {
        val rotated = geometry.rotateQuarterKeepingFrame(PhotoEdit())

        val rect = geometry.outputRect(rotated)
        assertEquals(0.75f, rect.width, TOLERANCE)
        assertEquals(4f / 3f, rect.width / rect.height, TOLERANCE)
        assertTrue(geometry.isInside(rotated))
    }

    @Test
    fun `네 번 회전하면 처음 상태로 돌아온다`() {
        val edit = PhotoEdit(straightenDegrees = 5f, crop = geometry.constrain(PhotoEdit(straightenDegrees = 5f)))

        val rotated = (1..4).fold(edit) { current, _ -> geometry.rotateQuarter(current) }

        assertEquals(0, rotated.quarterTurns)
        assertCrop(edit.crop, rotated.crop)
    }

    @Test
    fun `90도 회전한 이미지의 결과 영역은 세로 방향이다`() {
        val rect = geometry.outputRect(PhotoEdit(quarterTurns = 1))

        assertRect(CropRect(0f, 0f, 0.75f, 1f), rect)
    }

    @Test
    fun `회전한 이미지 전체가 뷰포트에 들어가는 배율을 계산한다`() {
        assertEquals(400f, geometry.fitScale(0f, viewportWidth = 400f, viewportHeight = 400f), TOLERANCE)
        assertEquals(400f, geometry.fitScale(90f, viewportWidth = 300f, viewportHeight = 400f), TOLERANCE)
    }

    private val CropRect.left get() = centerX - width / 2f
    private val CropRect.top get() = centerY - height / 2f

    private fun assertCrop(
        expected: CropWindow,
        actual: CropWindow,
    ) {
        assertEquals("centerX", expected.centerX, actual.centerX, TOLERANCE)
        assertEquals("centerY", expected.centerY, actual.centerY, TOLERANCE)
        assertEquals("width", expected.width, actual.width, TOLERANCE)
        assertEquals("height", expected.height, actual.height, TOLERANCE)
    }

    private fun assertRect(
        expected: CropRect,
        actual: CropRect,
    ) {
        assertEquals("centerX", expected.centerX, actual.centerX, TOLERANCE)
        assertEquals("centerY", expected.centerY, actual.centerY, TOLERANCE)
        assertEquals("width", expected.width, actual.width, TOLERANCE)
        assertEquals("height", expected.height, actual.height, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-3f
    }
}
