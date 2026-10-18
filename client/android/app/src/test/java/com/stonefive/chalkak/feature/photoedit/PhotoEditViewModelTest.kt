package com.stonefive.chalkak.feature.photoedit

import com.stonefive.chalkak.domain.model.CropWindow
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
    fun `90도 회전하면 고정한 비율도 반대 비율로 바뀐다`() {
        val viewModel = loadedViewModel()
        viewModel.onAspectRatioSelect(CropAspectRatio.Portrait4x5)

        viewModel.onRotateClick()

        assertEquals(1, viewModel.uiState.value.edit.quarterTurns)
        assertEquals(5f / 4f, viewModel.uiState.value.lockedAspectRatio!!, TOLERANCE)
        assertEquals(CropAspectRatio.Landscape5x4, viewModel.uiState.value.aspectRatio)

        viewModel.onRotateClick()

        assertEquals(4f / 5f, viewModel.uiState.value.lockedAspectRatio!!, TOLERANCE)
        assertEquals(CropAspectRatio.Portrait4x5, viewModel.uiState.value.aspectRatio)
    }

    @Test
    fun `반대 비율을 고르면 그 비율로 고정한다`() {
        val viewModel = loadedViewModel()

        viewModel.onAspectRatioSelect(CropAspectRatio.Portrait9x16)

        val state = viewModel.uiState.value
        assertEquals(9f / 16f, state.lockedAspectRatio!!, TOLERANCE)
        assertEquals(9f / 16f, (state.edit.crop.width * 4000f) / (state.edit.crop.height * 3000f), TOLERANCE)
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

    private fun loadedViewModel(initialEdit: PhotoEdit = PhotoEdit()) =
        PhotoEditViewModel(imageUri, initialEdit).apply {
            onImageLoaded(width = 4000, height = 3000)
        }

    private companion object {
        const val TOLERANCE = 1e-3f
    }
}
