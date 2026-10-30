package com.stonefive.chalkak.feature.record.component

import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostStatus
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordPhotoStackTest {
    @Test
    fun `주 순서와 지난 날 수는 일요일 시작 달력을 따른다`() {
        // 2026년 9월 1일은 화요일이다.
        assertEquals(0, Month.weekIndexOf(Month.atDay(5)))
        assertEquals(1, Month.weekIndexOf(Month.atDay(6)))
        assertEquals(2, Month.weekIndexOf(Month.atDay(13)))

        val today = LocalDate.of(2026, 9, 8)
        assertEquals(8, Month.elapsedDayCount(today))
        assertEquals(31, YearMonth.of(2026, 8).elapsedDayCount(today))
        assertEquals(0, YearMonth.of(2026, 10).elapsedDayCount(today))
    }

    @Test
    fun `묶음은 오늘까지 최근 날부터 채우고 기록 없는 날은 빈 장으로 둔다`() {
        val order = order(elapsedDays = 8, postDays = listOf(1, 6, 7))

        assertEquals(listOf(8, 7, 6, 5, 4, 3, 2, 1), order.entries.map { it.day })
        assertEquals(listOf(null, "7", "6", null, null, null, null, "1"), order.entries.map { it.post?.postId })
        assertEquals(2, order.indexOf(Month.atDay(6)))
    }

    @Test
    fun `보고 있는 장은 범위 안으로 맞추고 위치와 서로 바뀐다`() {
        val order = order(elapsedDays = 8, postDays = listOf(1))

        assertEquals(2, order.focusedIndex(2.4f))
        assertEquals(7, order.focusedIndex(9f))
        assertEquals(0, order.focusedIndex(-3f))
        assertNull(order(elapsedDays = 0, postDays = emptyList()).focusedIndex(0f))
        order.entries.indices.forEach { index ->
            assertEquals(index, order.focusedIndex(order.position(index)))
        }
    }

    @Test
    fun `한 달이 화면에 들어오면 1일을 왼쪽 끝에 붙이고 최근 날을 펼친다`() {
        val layout = RecordPillarLayout(entryCount = 13, position = 0f, width = WIDTH, previousShift = null)

        assertClose(20f, layout.centers[12] - layout.widths[12] / 2)
        assertTrue(layout.centers[12] < layout.centers[0])
        assertClose(150f, layout.widths[0])
    }

    @Test
    fun `한 달이 넘치면 가는 기둥을 좁히고 펼친 기둥은 화면 안에 둔다`() {
        val thin = RecordPillarLayout.thinWidth(entryCount = 30, width = WIDTH)
        assertTrue(thin in 8f..12f)

        var shift: Float? = null
        (0 until 30).forEach { position ->
            val layout = RecordPillarLayout(30, position.toFloat(), WIDTH, shift)
            shift = layout.shift
            val leftEdge = abs(layout.centers[29] - layout.widths[29] / 2 - 20f) < TOLERANCE
            val rightEdge = abs(layout.centers[0] + layout.widths[0] / 2 - (WIDTH - 20f)) < TOLERANCE
            assertTrue(layout.focusX >= 150f / 2 + 24f - TOLERANCE || leftEdge)
            assertTrue(layout.focusX <= WIDTH - 150f / 2 - 24f + TOLERANCE || rightEdge)
        }
    }

    @Test
    fun `펼친 기둥은 사진 비율대로 폭을 잡고 넓은 사진은 폭 상한에서 멈춘다`() {
        assertClose(168f, RecordPillarLayout.openSize(ratio = 4f / 3f))
        assertClose(94.5f, RecordPillarLayout.openSize(ratio = 3f / 4f))
        assertClose(240f, RecordPillarLayout.openSize(ratio = 3f))
        assertClose(150f, RecordPillarLayout.openSize(ratio = null))
        // 묶음이 커지면 기둥이 커지고, 4:3 사진도 낮아지지 않고 다 들어간다.
        assertClose(236f, RecordPillarLayout.pillarHeight(stackHeight = 410f))
        assertClose(236f * 4f / 3f, RecordPillarLayout.openSize(ratio = 4f / 3f, pillarHeight = 236f, width = WIDTH))
    }

    @Test
    fun `끄는 위치는 날짜 가까이에서 걸리고 끝 밖은 그대로 둔다`() {
        assertEquals(3f, RecordPillarLayout.detented(3.2f, entryCount = 13))
        assertEquals(3.5f, RecordPillarLayout.detented(3.5f, entryCount = 13))
        assertEquals(4f, RecordPillarLayout.detented(3.8f, entryCount = 13))
        assertEquals(-0.2f, RecordPillarLayout.detented(-0.2f, entryCount = 13))
        assertEquals(12.3f, RecordPillarLayout.detented(12.3f, entryCount = 13))

        var previous = 0f
        (0..120).forEach { step ->
            val value = RecordPillarLayout.detented(step / 10f, entryCount = 13)
            assertTrue(value >= previous)
            previous = value
        }
    }

    @Test
    fun `끝을 넘겨 끌면 기둥 폭은 그대로 두고 줄만 밀린다`() {
        val atEnd = RecordPillarLayout(13, 12f, WIDTH, null)
        val beyond = RecordPillarLayout(13, 12.6f, WIDTH, null)

        assertEquals(atEnd.widths, beyond.widths)
        assertTrue(beyond.centers[12] < atEnd.centers[12])
    }

    @Test
    fun `책은 표지와 속표지 뒤에 1일부터 오늘까지 한 장씩 둔다`() {
        val model = RecordBookModel(
            month = Month,
            posts = listOf(1, 6, 13).map(::post),
            today = Month.atDay(12),
        )

        assertEquals(14, model.maxSpread)
        assertNull(model.day(0))
        assertNull(model.day(1))
        assertEquals(1, model.day(2)?.day)
        assertEquals("13", model.post(14)?.postId)
        assertEquals(14f, model.spread(0f))
        assertEquals(2f, model.spread(12f))
        assertEquals(0f, model.spread(14f))
    }

    @Test
    fun `책 사진은 원래 비율로 오른쪽 면 안에 들어간다`() {
        listOf(3f / 4f, 4f / 3f, 1f, 3f).forEach { ratio ->
            val photo = RecordBookGeometry.photoFrame(ratio)
            assertClose(ratio, photo.width / photo.height)
            assertTrue(photo.left >= 393f / 2)
            assertTrue(photo.left + photo.width <= 393f / 2 + 152f)
            assertTrue(photo.top >= 72f)
            assertTrue(photo.top + photo.height <= 72f + 198f)
        }
    }

    @Test
    fun `휘는 종이는 책등에서 끝으로 갈수록 더 넘어가고 넘기는 중에는 앞뒷면이 갈린다`() {
        val segments = RecordPaperCurl.segments(turn = 0.4f, direction = 1f, width = 152f, height = 198f, count = 10)

        assertEquals(10, segments.size)
        segments.zipWithNext().forEach { (start, end) -> assertTrue(end.angle >= start.angle) }
        assertTrue(segments.first().showsFront)
        assertTrue(!segments.last().showsFront)

        val flat = RecordPaperCurl.segments(turn = 0f, direction = 1f, width = 152f, height = 198f, count = 10)
        flat.forEach { assertClose(0f, it.angle) }
    }

    private fun order(
        elapsedDays: Int,
        postDays: List<Int>,
    ) = RecordPhotoStackOrder(Month, postDays.map(::post), elapsedDays)

    private fun post(day: Int) = PostCalendarItem(
        postId = "$day",
        topicDate = Month.atDay(day),
        thumbnailImageUrl = "",
        status = PostStatus.APPROVED,
    )

    private fun assertClose(
        expected: Float,
        actual: Float,
    ) = assertEquals(expected, actual, TOLERANCE)

    private companion object {
        val Month: YearMonth = YearMonth.of(2026, 9)
        const val WIDTH = 393f
        const val TOLERANCE = 0.001f
    }
}
