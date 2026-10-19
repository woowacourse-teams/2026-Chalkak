package com.stonefive.chalkak.feature.photoedit

import com.stonefive.chalkak.domain.model.CropWindow
import com.stonefive.chalkak.domain.model.PhotoAdjustment
import com.stonefive.chalkak.domain.model.PhotoAdjustments
import com.stonefive.chalkak.domain.model.PhotoEdit
import com.stonefive.chalkak.domain.model.PhotoFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoEditViewModelTest {
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
        val viewModel = PhotoEditViewModel(imageUri, PhotoEdit())

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

        viewModel.onAdjustmentChange(0.3f)
        viewModel.onAdjustmentSelect(PhotoAdjustment.Vignette)
        viewModel.onAdjustmentChange(-0.5f)

        assertEquals(
            PhotoAdjustments(brightness = 0.3f, vignette = -0.5f),
            viewModel.uiState.value.edit.adjustments,
        )
    }

    @Test
    fun `조정 초기화는 조정만 되돌리고 자르기 초기화는 조정을 유지한다`() {
        val viewModel = loadedViewModel()
        viewModel.onAdjustmentChange(0.3f)
        viewModel.onRotateClick()

        viewModel.onResetClick()
        assertEquals(PhotoAdjustments(brightness = 0.3f), viewModel.uiState.value.edit.adjustments)

        viewModel.onRotateClick()
        viewModel.onAdjustmentsResetClick()
        assertEquals(PhotoEdit(quarterTurns = 1), viewModel.uiState.value.edit)
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

    private fun outputAspectRatio(edit: PhotoEdit): Float {
        val rect = CropGeometry(4000f, 3000f).outputRect(edit)
        return rect.width / rect.height
    }

    private fun loadedViewModel(initialEdit: PhotoEdit = PhotoEdit()) =
        PhotoEditViewModel(imageUri, initialEdit).apply {
            onImageLoaded(width = 4000, height = 3000)
        }

    private companion object {
        const val TOLERANCE = 1e-3f
    }
}
