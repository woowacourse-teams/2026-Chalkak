package com.stonefive.chalkak.feature.upload

import com.stonefive.chalkak.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhotoUploadViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModel = PhotoUploadViewModel()

    @Test
    fun `사진을 선택하면 제출할 수 있다`() {
        val image = "content://media/photo/1"

        viewModel.onImageSelected(image)

        assertSame(image, viewModel.uiState.value.selectedImage)
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `캡션 변경은 상태에 반영된다`() {
        viewModel.onAction(PhotoUploadUiAction.CaptionChanged("오늘의 사진"))

        assertEquals("오늘의 사진", viewModel.uiState.value.caption)
    }

    @Test
    fun `사진이 없으면 제출 이벤트를 보내지 않는다`() {
        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `사진이 있으면 성공 화면 이동 이벤트를 보낸다`() = runTest {
        val image = "content://media/photo/1"
        viewModel.onImageSelected(image)
        viewModel.onAction(PhotoUploadUiAction.CaptionChanged("한낮의 다리"))

        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(
            PhotoUploadUiEvent.NavigateToSuccess(
                imageModel = image,
                caption = "한낮의 다리",
                content = PhotoUploadSuccessContent(),
            ),
            viewModel.uiEvent.first(),
        )
    }

    @Test
    fun `초기화하면 사진과 캡션을 제거한다`() {
        viewModel.onImageSelected("content://media/photo/1")
        viewModel.onAction(PhotoUploadUiAction.CaptionChanged("오늘의 사진"))

        viewModel.reset()

        assertEquals(PhotoUploadUiState(), viewModel.uiState.value)
    }
}
