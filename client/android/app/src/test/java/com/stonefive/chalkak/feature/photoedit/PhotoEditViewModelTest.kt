package com.stonefive.chalkak.feature.photoedit

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.photoedit.CropWindow
import com.stonefive.chalkak.domain.model.photoedit.PhotoAdjustment
import com.stonefive.chalkak.domain.model.photoedit.PhotoAdjustments
import com.stonefive.chalkak.domain.model.photoedit.PhotoAutoAdjuster
import com.stonefive.chalkak.domain.model.photoedit.PhotoEdit
import com.stonefive.chalkak.domain.model.photoedit.PhotoFilter
import com.stonefive.chalkak.domain.model.photoedit.PhotoImageStatistics
import com.stonefive.chalkak.domain.model.photoedit.PhotoPixels
import com.stonefive.chalkak.domain.repository.PhotoEditImageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoEditViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val imageUri = "content://media/photo/1"

    @Test
    fun `처음 고른 사진은 편집하지 않은 상태로 시작한다`() {
        val viewModel = loadedViewModel()

        assertEquals(PhotoEdit(), viewModel.uiState.value.edit)
        assertFalse(viewModel.uiState.value.canReset)
        assertNull(viewModel.uiState.value.completedEdit)
    }

    @Test
    fun `다시 편집하면 이전 편집 값에서 시작한다`() {
        val previous = PhotoEdit(
            quarterTurns = 1,
            crop = CropWindow(centerX = 0.5f, centerY = 0.5f, width = 0.5f, height = 0.5f),
            filter = PhotoFilter.Warm,
        )

        val viewModel = loadedViewModel(previous)

        assertEquals(previous, viewModel.uiState.value.edit)
        assertTrue(viewModel.uiState.value.canReset)
    }

    @Test
    fun `이미지를 불러오기 전에는 자르기 조작을 무시한다`() {
        val viewModel = PhotoEditViewModel(imageUri, PhotoEdit(), FakeImageRepository(pixels = null))

        viewModel.onRotateClick()
        viewModel.onAspectRatioSelect(CropAspectRatio.Square)

        assertEquals(PhotoEdit(), viewModel.uiState.value.edit)
    }

    @Test
    fun `90도 회전해도 고른 비율의 프레임 모양은 그대로다`() {
        val viewModel = loadedViewModel()
        viewModel.onAspectRatioSelect(CropAspectRatio.Portrait4x5)

        viewModel.onRotateClick()

        val state = viewModel.uiState.value
        assertEquals(1, state.edit.quarterTurns)
        assertEquals(CropAspectRatio.Portrait4x5, state.aspectRatio)
        assertEquals(4f / 5f, state.lockedAspectRatio!!, TOLERANCE)
        assertEquals(4f / 5f, outputAspectRatio(state.edit), TOLERANCE)
    }

    @Test
    fun `고정 비율로 회전하면 비율을 다시 고른 것처럼 가장 크게 맞춘다`() {
        val viewModel = loadedViewModel()
        viewModel.onAspectRatioSelect(CropAspectRatio.Landscape5x4)

        repeat(2) {
            viewModel.onRotateClick()
            val rotated = viewModel.uiState.value.edit

            viewModel.onAspectRatioSelect(CropAspectRatio.Landscape5x4)

            val reselected = viewModel.uiState.value.edit
            assertEquals(reselected.crop.width, rotated.crop.width, TOLERANCE)
            assertEquals(reselected.crop.height, rotated.crop.height, TOLERANCE)
            assertEquals(5f / 4f, outputAspectRatio(rotated), TOLERANCE)
        }
    }

    @Test
    fun `자른 프레임은 자유 비율에서도 회전 후 모양을 유지한다`() {
        val viewModel = loadedViewModel()
        viewModel.onCropResize(CropCorner.BottomEnd, deltaX = -0.5f, deltaY = 0f)
        val before = outputAspectRatio(viewModel.uiState.value.edit)

        viewModel.onRotateClick()

        assertEquals(before, outputAspectRatio(viewModel.uiState.value.edit), TOLERANCE)
    }

    @Test
    fun `자르지 않은 사진과 원본 비율은 사진과 함께 돈다`() {
        val free = loadedViewModel()
        free.onRotateClick()
        assertEquals(PhotoEdit(quarterTurns = 1), free.uiState.value.edit)

        val original = loadedViewModel()
        original.onAspectRatioSelect(CropAspectRatio.Original)
        original.onRotateClick()
        assertEquals(CropWindow.Full, original.uiState.value.edit.crop)
        assertEquals(3f / 4f, original.uiState.value.lockedAspectRatio!!, TOLERANCE)
    }

    @Test
    fun `반대 비율을 고르면 그 비율로 고정한다`() {
        val viewModel = loadedViewModel()

        viewModel.onAspectRatioSelect(CropAspectRatio.Portrait9x16)

        val state = viewModel.uiState.value
        assertEquals(9f / 16f, state.lockedAspectRatio!!, TOLERANCE)
        assertEquals(9f / 16f, outputAspectRatio(state.edit), TOLERANCE)
    }

    @Test
    fun `원본 비율은 현재 회전 방향의 이미지 비율로 고정한다`() {
        val viewModel = loadedViewModel()
        viewModel.onRotateClick()

        viewModel.onAspectRatioSelect(CropAspectRatio.Original)

        assertEquals(3f / 4f, viewModel.uiState.value.lockedAspectRatio!!, TOLERANCE)
        assertEquals(3f / 4f, viewModel.uiState.value.originalAspectRatio!!, TOLERANCE)
        assertEquals(CropWindow.Full, viewModel.uiState.value.edit.crop)
    }

    @Test
    fun `자르기를 원래 크기로 되돌리면 편집하지 않은 사진으로 완료한다`() {
        val viewModel = loadedViewModel()

        viewModel.onCropResize(CropCorner.BottomEnd, deltaX = -0.3f, deltaY = -0.3f)
        assertTrue(viewModel.uiState.value.edit.crop.width < 1f)
        viewModel.onCropResize(CropCorner.BottomEnd, deltaX = 0.3f, deltaY = 0.3f)
        viewModel.onDoneClick()

        assertEquals(PhotoEdit(), viewModel.uiState.value.completedEdit)
        assertTrue(
            viewModel.uiState.value.completedEdit!!
                .isIdentity,
        )
    }

    @Test
    fun `초기화하면 자르기와 회전만 되돌리고 필터는 유지한다`() {
        val viewModel = loadedViewModel()
        viewModel.onFilterSelect(PhotoFilter.Mono)
        viewModel.onRotateClick()
        viewModel.onAspectRatioSelect(CropAspectRatio.Square)

        viewModel.onResetClick()

        assertEquals(PhotoEdit(filter = PhotoFilter.Mono), viewModel.uiState.value.edit)
        assertEquals(CropAspectRatio.Free, viewModel.uiState.value.aspectRatio)
        assertNull(viewModel.uiState.value.lockedAspectRatio)
        assertFalse(viewModel.uiState.value.canReset)
    }

    @Test
    fun `조정 값은 고른 항목에만 적용한다`() {
        val viewModel = loadedViewModel()

        viewModel.onAdjustItemSelect(PhotoAdjustItem.Manual(PhotoAdjustment.Exposure))
        viewModel.onAdjustmentChange(0.3f)
        viewModel.onAdjustItemSelect(PhotoAdjustItem.Manual(PhotoAdjustment.Shadows))
        viewModel.onAdjustmentChange(-0.5f)

        assertEquals(
            PhotoAdjustments(exposure = 0.3f, shadows = -0.5f),
            viewModel.uiState.value.edit.adjustments,
        )
    }

    @Test
    fun `조정 초기화는 조정만 되돌리고 자르기 초기화는 조정을 유지한다`() {
        val viewModel = loadedViewModel(pixels = lowContrastPixels())
        viewModel.onAdjustItemSelect(PhotoAdjustItem.Auto)
        viewModel.onRotateClick()

        viewModel.onResetClick()
        assertEquals(1f, viewModel.uiState.value.edit.adjustmentLayers.intensity)
        assertFalse(viewModel.uiState.value.edit.adjustments.isIdentity)

        viewModel.onRotateClick()
        viewModel.onAdjustmentsResetClick()
        val edit = viewModel.uiState.value.edit
        assertEquals(1, edit.quarterTurns)
        assertTrue(edit.adjustments.isIdentity)
        assertFalse(viewModel.uiState.value.canResetAdjustments)
    }

    @Test
    fun `자동 보정은 처음 고르면 분석한 값을 그대로 채우고 다시 누르면 끈다`() {
        val pixels = lowContrastPixels()
        val viewModel = loadedViewModel(pixels = pixels)

        viewModel.onAdjustItemSelect(PhotoAdjustItem.Auto)

        val automatic = PhotoAutoAdjuster.adjustments(PhotoImageStatistics.of(pixels))
        assertTrue(automatic.exposure > 0f)
        assertEquals(1f, viewModel.uiState.value.edit.adjustmentLayers.intensity)
        assertEquals(automatic, viewModel.uiState.value.edit.adjustments)

        viewModel.onAdjustmentChange(0.5f)
        assertEquals(
            automatic.scaled(scale = 0.5f, offset = PhotoAdjustments()),
            viewModel.uiState.value.edit.adjustments,
        )

        viewModel.onAdjustItemSelect(PhotoAdjustItem.Auto)
        assertEquals(0f, viewModel.uiState.value.edit.adjustmentLayers.intensity)
        assertEquals(PhotoAdjustments(), viewModel.uiState.value.edit.adjustments)
    }

    @Test
    fun `자동 보정 위에서 직접 만진 항목은 강도를 바꾸거나 꺼도 그 값을 유지한다`() {
        val viewModel = loadedViewModel(pixels = lowContrastPixels())
        viewModel.onAdjustItemSelect(PhotoAdjustItem.Auto)
        val automatic = viewModel.uiState.value.edit.adjustments
        assertTrue(automatic.exposure > 0.1f && automatic.contrast > 0f)
        val lowered = automatic.exposure - 0.1f

        viewModel.onAdjustItemSelect(PhotoAdjustItem.Manual(PhotoAdjustment.Exposure))
        viewModel.onAdjustmentChange(lowered)

        // 강도를 반으로 줄이면 직접 만진 노출은 그대로, 만지지 않은 대비만 반으로 준다.
        viewModel.onAdjustItemSelect(PhotoAdjustItem.Auto)
        viewModel.onAdjustmentChange(0.5f)
        assertEquals(lowered, viewModel.uiState.value.edit.adjustments.exposure, TOLERANCE)
        assertEquals(automatic.contrast * 0.5f, viewModel.uiState.value.edit.adjustments.contrast, TOLERANCE)

        // 자동 보정을 꺼도 직접 만진 노출은 원본보다 어두워지지 않고 그 값으로 남는다.
        viewModel.onAdjustmentChange(0f)
        assertEquals(lowered, viewModel.uiState.value.edit.adjustments.exposure, TOLERANCE)
        assertEquals(0f, viewModel.uiState.value.edit.adjustments.contrast, TOLERANCE)
    }

    @Test
    fun `다시 편집하면 자동 보정과 사용자 값을 그대로 이어 가고 같은 자르기는 다시 분석하지 않는다`() {
        val first = loadedViewModel(pixels = lowContrastPixels())
        first.onAdjustItemSelect(PhotoAdjustItem.Auto)
        first.onAdjustItemSelect(PhotoAdjustItem.Manual(PhotoAdjustment.Warmth))
        first.onAdjustmentChange(0.3f)
        first.onDoneClick()
        val completed = first.uiState.value.completedEdit!!

        val second = loadedViewModel(initialEdit = completed, pixels = halfDarkPixels())

        assertEquals(completed.adjustmentLayers, second.uiState.value.edit.adjustmentLayers)
        second.onAdjustItemSelect(PhotoAdjustItem.Auto)
        assertEquals(PhotoAdjustments(warmth = 0.3f), second.uiState.value.edit.adjustments)
    }

    @Test
    fun `직접 만진 값은 자동 보정을 켜고 다시 편집해 끄더라도 그대로다`() {
        val first = loadedViewModel(pixels = lowContrastPixels())
        first.onAdjustItemSelect(PhotoAdjustItem.Manual(PhotoAdjustment.Exposure))
        first.onAdjustmentChange(1f)
        first.onAdjustItemSelect(PhotoAdjustItem.Auto)
        first.onDoneClick()
        val completed = first.uiState.value.completedEdit!!
        assertEquals(1f, completed.adjustments.exposure)

        val second = loadedViewModel(initialEdit = completed, pixels = lowContrastPixels())
        second.onAdjustItemSelect(PhotoAdjustItem.Auto)

        assertEquals(0f, second.uiState.value.edit.adjustmentLayers.intensity)
        assertEquals(1f, second.uiState.value.edit.adjustments.exposure)
    }

    @Test
    fun `자동 보정을 끄고 완료하면 분석한 값은 결과에 남기지 않는다`() {
        val viewModel = loadedViewModel(pixels = lowContrastPixels())

        viewModel.onDoneClick()

        assertEquals(PhotoEdit(), viewModel.uiState.value.completedEdit)
    }

    @Test
    fun `자르기를 바꾸고 완료하면 자른 사진으로 자동 보정값을 다시 구한다`() {
        val viewModel = loadedViewModel(pixels = halfDarkPixels())
        viewModel.onAdjustItemSelect(PhotoAdjustItem.Auto)
        val whole = viewModel.uiState.value.edit.adjustments

        viewModel.onCropResize(CropCorner.BottomEnd, deltaX = -2000f, deltaY = 0f)
        viewModel.onDoneClick()

        val completed = viewModel.uiState.value.completedEdit!!
        assertNotEquals(whole, completed.adjustments)
        assertTrue(completed.adjustments.exposure > whole.exposure)
    }

    @Test
    fun `완료하면 현재 편집 값을 결과로 내보낸다`() {
        val viewModel = loadedViewModel()
        viewModel.onAspectRatioSelect(CropAspectRatio.Square)
        viewModel.onCropResize(CropCorner.BottomEnd, deltaX = -0.2f, deltaY = -0.2f)
        viewModel.onFilterSelect(PhotoFilter.Vintage)

        viewModel.onDoneClick()

        val completed = viewModel.uiState.value.completedEdit!!
        assertEquals(viewModel.uiState.value.edit, completed)
        assertEquals(PhotoFilter.Vintage, completed.filter)
        assertEquals(0.55f, completed.crop.width, TOLERANCE)
        assertEquals(0.55f / 0.75f, completed.crop.height, TOLERANCE)
    }

    @Test
    fun `완료하거나 취소하면 분석용 사진을 놓아 더는 다시 분석하지 않는다`() {
        listOf(PhotoEditViewModel::onDoneClick, PhotoEditViewModel::onCancel).forEach { finish ->
            val viewModel = loadedViewModel(pixels = halfDarkPixels())
            viewModel.onAdjustItemSelect(PhotoAdjustItem.Auto)
            val automatic = viewModel.uiState.value.edit.adjustmentLayers.automatic

            finish(viewModel)
            viewModel.onCropResize(CropCorner.BottomEnd, deltaX = -2000f, deltaY = 0f)
            viewModel.onTabSelect(PhotoEditTab.Adjust)

            assertEquals(automatic, viewModel.uiState.value.edit.adjustmentLayers.automatic)
        }
    }

    private fun outputAspectRatio(edit: PhotoEdit): Float {
        val rect = CropGeometry(4000f, 3000f).centeredRect(edit)
        return rect.width / rect.height
    }

    private fun loadedViewModel(
        initialEdit: PhotoEdit = PhotoEdit(),
        pixels: PhotoPixels? = null,
    ) = PhotoEditViewModel(
        imageUri = imageUri,
        initialEdit = initialEdit,
        imageRepository = FakeImageRepository(pixels),
        analysisDispatcher = UnconfinedTestDispatcher(),
    ).apply {
        onImageLoaded(width = 4000, height = 3000)
    }

    // 4:3 비율에 밝기 90..150 사이를 가로로 오가는 뜬 회색.
    private fun lowContrastPixels() = pixels { x -> 90 + x * 60 / (PIXELS_WIDTH - 1) }

    // 왼쪽 절반은 어둡고 오른쪽 절반은 밝은 사진.
    private fun halfDarkPixels() = pixels { x -> if (x < PIXELS_WIDTH / 2) 30 else 220 }

    private fun pixels(levelAt: (Int) -> Int) = PhotoPixels(
        width = PIXELS_WIDTH,
        height = PIXELS_HEIGHT,
        argb = IntArray(PIXELS_WIDTH * PIXELS_HEIGHT) { index ->
            val level = levelAt(index % PIXELS_WIDTH)
            (0xFF shl 24) or (level shl 16) or (level shl 8) or level
        },
    )

    private class FakeImageRepository(private val pixels: PhotoPixels?) : PhotoEditImageRepository {
        override suspend fun loadPixels(
            imageUri: String,
            minLongEdge: Int,
        ): PhotoPixels? = pixels
    }

    private companion object {
        const val TOLERANCE = 1e-3f
        const val PIXELS_WIDTH = 40
        const val PIXELS_HEIGHT = 30
    }
}
