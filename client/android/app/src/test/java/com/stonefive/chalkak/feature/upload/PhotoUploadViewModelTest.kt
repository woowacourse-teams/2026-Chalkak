package com.stonefive.chalkak.feature.upload

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.PostCreation
import com.stonefive.chalkak.domain.model.PostCreationFailure
import com.stonefive.chalkak.domain.model.PostCreationResult
import com.stonefive.chalkak.domain.model.PostModerationStatus
import com.stonefive.chalkak.domain.repository.PostRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
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

    private val postRepository = FakePostRepository()
    private val uploadTopicDate = LocalDate.of(2026, 8, 29)
    private val viewModel = PhotoUploadViewModel(postRepository, uploadTopicDate)

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
    fun `사진이 없으면 제출하지 않는다`() {
        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(0, postRepository.callCount)
    }

    @Test
    fun `성공 결과는 실제 제출 정보와 함께 durable 상태가 된다`() = runTest {
        postRepository.result = PostCreationResult.Success(
            PostCreation(
                postId = "post-id",
                topicId = "topic-id",
                topic = "바다",
                topicDate = LocalDate.of(2026, 8, 29),
                moderationStatus = PostModerationStatus.PENDING,
            ),
        )
        val image = "content://media/photo/1"
        viewModel.onImageSelected(image)
        viewModel.onAction(PhotoUploadUiAction.CaptionChanged("한낮의 다리"))

        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(
            PhotoUploadSubmission(
                imageModel = image,
                caption = "한낮의 다리",
                content = PhotoUploadSuccessContent(
                    dateLabel = "2026. 08. 29",
                    topic = "바다",
                    moderationStatus = "PENDING",
                ),
            ),
            viewModel.uiState.value.completedSubmission,
        )
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `처리 중 반복 탭은 repository를 한 번만 호출한다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        postRepository.await = gate
        viewModel.onImageSelected("content://media/photo/1")

        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)
        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(1, postRepository.callCount)
        assertEquals(uploadTopicDate, postRepository.requestedTopicDates.single())
        assertTrue(viewModel.uiState.value.isSubmitting)

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `자정이 지나도 업로드 화면 진입 시 고정한 주제 날짜로 제출한다`() = runTest {
        viewModel.onImageSelected("content://media/photo/1")

        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(listOf(uploadTopicDate), postRepository.requestedTopicDates)
    }

    @Test
    fun `실패하면 입력을 유지하고 다시 제출할 수 있다`() = runTest {
        postRepository.results.add(PostCreationResult.Failure(PostCreationFailure.NetworkUnavailable))
        postRepository.results.add(
            PostCreationResult.Success(
                PostCreation(
                    postId = "post-id",
                    topicId = "topic-id",
                    topic = "바다",
                    topicDate = LocalDate.of(2026, 8, 29),
                    moderationStatus = PostModerationStatus.VALIDATING,
                ),
            ),
        )
        val image = "content://media/photo/1"
        viewModel.onImageSelected(image)
        viewModel.onAction(PhotoUploadUiAction.CaptionChanged("제목"))

        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(image, viewModel.uiState.value.selectedImage)
        assertEquals("제목", viewModel.uiState.value.caption)
        assertEquals("네트워크 연결을 확인해 주세요.", viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.canSubmit)

        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(2, postRepository.callCount)
        assertTrue(viewModel.uiState.value.completedSubmission != null)
    }

    @Test
    fun `중복 제출 실패는 구체적인 메시지와 입력을 유지한다`() = runTest {
        postRepository.result = PostCreationResult.Failure(PostCreationFailure.AlreadySubmitted)
        val image = "content://media/photo/1"
        viewModel.onImageSelected(image)
        viewModel.onAction(PhotoUploadUiAction.CaptionChanged("제목"))

        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals("이미 이 주제에 전시한 사진이 있어요.", viewModel.uiState.value.errorMessage)
        assertEquals(image, viewModel.uiState.value.selectedImage)
        assertEquals("제목", viewModel.uiState.value.caption)
    }

    @Test
    fun `닫힌 주제 실패는 주제 변경 메시지와 입력을 유지한다`() = runTest {
        postRepository.result = PostCreationResult.Failure(PostCreationFailure.TopicNotOpen)
        val image = "content://media/photo/1"
        viewModel.onImageSelected(image)
        viewModel.onAction(PhotoUploadUiAction.CaptionChanged("제목"))

        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(
            "주제가 변경되어 전시할 수 없어요.",
            viewModel.uiState.value.errorMessage,
        )
        assertEquals(image, viewModel.uiState.value.selectedImage)
        assertEquals("제목", viewModel.uiState.value.caption)
    }

    @Test
    fun `401은 재인증 effect를 보내고 입력은 유지한다`() = runTest {
        postRepository.result = PostCreationResult.Failure(
            PostCreationFailure.ReauthenticationRequired,
        )
        val image = "content://media/photo/1"
        viewModel.onImageSelected(image)

        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(PhotoUploadUiEvent.ReauthenticationRequired, viewModel.uiEvent.first())
        assertEquals(image, viewModel.uiState.value.selectedImage)
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `초기화하면 사진과 캡션을 제거한다`() {
        viewModel.onImageSelected("content://media/photo/1")
        viewModel.onAction(PhotoUploadUiAction.CaptionChanged("오늘의 사진"))

        viewModel.reset()

        assertEquals(PhotoUploadUiState(), viewModel.uiState.value)
    }
}

private class FakePostRepository : PostRepository {
    var result: PostCreationResult = PostCreationResult.Failure(
        PostCreationFailure.NetworkUnavailable,
    )
    val results = ArrayDeque<PostCreationResult>()
    var await: CompletableDeferred<Unit>? = null
    var callCount = 0
    val requestedTopicDates = mutableListOf<LocalDate>()

    override suspend fun createPost(
        imageUri: String,
        title: String?,
        topicDate: LocalDate,
    ): PostCreationResult {
        callCount++
        requestedTopicDates += topicDate
        await?.await()
        return if (results.isEmpty()) result else results.removeFirst()
    }
}
