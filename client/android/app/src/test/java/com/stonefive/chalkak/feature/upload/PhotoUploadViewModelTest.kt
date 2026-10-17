package com.stonefive.chalkak.feature.upload

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.CropWindow
import com.stonefive.chalkak.domain.model.PhotoEdit
import com.stonefive.chalkak.domain.model.PhotoFilter
import com.stonefive.chalkak.domain.model.PostCreation
import com.stonefive.chalkak.domain.model.PostCreationFailure
import com.stonefive.chalkak.domain.model.PostCreationResult
import com.stonefive.chalkak.domain.model.PostCreationTopicResult
import com.stonefive.chalkak.domain.model.PostImagePreparation
import com.stonefive.chalkak.domain.model.PostImagePreparationResult
import com.stonefive.chalkak.domain.model.PostModerationStatus
import com.stonefive.chalkak.domain.model.Topic
import com.stonefive.chalkak.domain.repository.PostCreationRepository
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

    private val postCreationRepository = FakePostCreationRepository()
    private val uploadTopicDate = LocalDate.of(2026, 8, 29)
    private val viewModel by lazy { PhotoUploadViewModel(postCreationRepository, uploadTopicDate) }

    @Test
    fun `업로드 화면을 열면 고정한 날짜의 주제를 미리 조회한다`() {
        assertFalse(viewModel.uiState.value.isTopicLoading)
        assertEquals(listOf(uploadTopicDate), postCreationRepository.requestedTopicDates)
        assertEquals(0, postCreationRepository.callCount)
    }

    @Test
    fun `캐시가 있으면 즉시 노출하고 재조회한 주제가 다르면 교체한다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakePostCreationRepository().apply {
            cachedTopic = Topic("cached-topic-id", "틈", uploadTopicDate)
            topicAwait = gate
            topicResult = PostCreationTopicResult.Success(Topic("fresh-topic-id", "빛", uploadTopicDate))
        }

        val cachedViewModel = PhotoUploadViewModel(repository, uploadTopicDate)

        assertEquals("틈", cachedViewModel.uiState.value.topicTitle)
        assertTrue(cachedViewModel.uiState.value.isTopicLoading)

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals("빛", cachedViewModel.uiState.value.topicTitle)
        assertFalse(cachedViewModel.uiState.value.isTopicLoading)
    }

    @Test
    fun `캐시가 없으면 재조회 완료 전까지 주제 문구를 노출하지 않는다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakePostCreationRepository().apply { topicAwait = gate }

        val uncachedViewModel = PhotoUploadViewModel(repository, uploadTopicDate)

        assertEquals(null, uncachedViewModel.uiState.value.topicTitle)
        assertTrue(uncachedViewModel.uiState.value.isTopicLoading)

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals(repository.defaultTopic.title, uncachedViewModel.uiState.value.topicTitle)
    }

    @Test
    fun `사진을 선택하면 제출할 수 있다`() {
        val image = "content://media/photo/1"

        viewModel.selectPhoto(image)

        assertSame(image, viewModel.uiState.value.selectedImage)
        assertEquals(1, postCreationRepository.prepareCount)
        assertEquals(ImagePreparationStatus.Ready, viewModel.uiState.value.imagePreparationStatus)
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `사진을 고르면 편집 화면을 띄우고 편집을 완료해야 준비를 시작한다`() {
        val image = "content://media/photo/1"

        viewModel.onImageSelected(image)

        assertEquals(
            PhotoEditRequest(id = 0, imageUri = image, initialEdit = PhotoEdit()),
            viewModel.uiState.value.editingPhoto,
        )
        assertEquals(null, viewModel.uiState.value.selectedImage)
        assertEquals(0, postCreationRepository.prepareCount)

        viewModel.onEditConfirmed(image, croppedMonoEdit)

        assertEquals(null, viewModel.uiState.value.editingPhoto)
        assertEquals(image, viewModel.uiState.value.selectedImage)
        assertEquals(croppedMonoEdit, viewModel.uiState.value.selectedEdit)
        assertEquals(listOf(croppedMonoEdit), postCreationRepository.preparedEdits)
        assertEquals(ImagePreparationStatus.Ready, viewModel.uiState.value.imagePreparationStatus)
    }

    @Test
    fun `카메라로 찍은 사진도 편집 화면을 먼저 띄운다`() {
        val image = "content://media/capture/1"

        viewModel.onCapturedImageCompleted(image, captureFilePath = null, saveToGallery = false)

        assertEquals(
            image,
            viewModel.uiState.value.editingPhoto
                ?.imageUri,
        )
        assertEquals(0, postCreationRepository.prepareCount)
    }

    @Test
    fun `처음 고른 사진의 편집을 취소하면 선택하지 않는다`() {
        viewModel.onImageSelected("content://media/photo/1")

        viewModel.onEditCanceled()

        assertEquals(null, viewModel.uiState.value.editingPhoto)
        assertEquals(null, viewModel.uiState.value.selectedImage)
        assertFalse(viewModel.uiState.value.canSubmit)
        assertEquals(0, postCreationRepository.prepareCount)
    }

    @Test
    fun `다른 사진을 고른 뒤 편집을 취소하면 기존 사진과 준비 결과를 유지한다`() {
        viewModel.selectPhoto("content://media/photo/1", croppedMonoEdit)

        viewModel.onImageSelected("content://media/photo/2")
        viewModel.onEditCanceled()

        assertEquals("content://media/photo/1", viewModel.uiState.value.selectedImage)
        assertEquals(croppedMonoEdit, viewModel.uiState.value.selectedEdit)
        assertTrue(postCreationRepository.discardedPreparations.isEmpty())
        assertEquals(1, postCreationRepository.prepareCount)
    }

    @Test
    fun `사진을 탭하면 현재 편집 값으로 다시 편집하고 취소하면 이전 편집을 유지한다`() {
        val image = "content://media/photo/1"
        viewModel.selectPhoto(image, croppedMonoEdit)

        viewModel.onPhotoClick()

        assertEquals(
            PhotoEditRequest(id = 1, imageUri = image, initialEdit = croppedMonoEdit),
            viewModel.uiState.value.editingPhoto,
        )

        viewModel.onEditCanceled()

        assertEquals(croppedMonoEdit, viewModel.uiState.value.selectedEdit)
        assertEquals(1, postCreationRepository.prepareCount)
        assertTrue(postCreationRepository.discardedPreparations.isEmpty())
    }

    @Test
    fun `다시 편집을 완료하면 이전 준비 결과를 폐기하고 새 편집으로 준비한다`() {
        val image = "content://media/photo/1"
        viewModel.selectPhoto(image)
        val firstPreparation = postCreationRepository.preparations.single()

        viewModel.onPhotoClick()
        viewModel.onEditConfirmed(image, croppedMonoEdit)

        assertEquals(listOf(firstPreparation), postCreationRepository.discardedPreparations)
        assertEquals(listOf(PhotoEdit(), croppedMonoEdit), postCreationRepository.preparedEdits)
        assertEquals(croppedMonoEdit, viewModel.uiState.value.selectedEdit)
        assertEquals(ImagePreparationStatus.Ready, viewModel.uiState.value.imagePreparationStatus)
    }

    @Test
    fun `준비 중에 다시 편집을 완료하면 이전 준비를 취소한다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        postCreationRepository.prepareAwait = gate
        val image = "content://media/photo/1"
        viewModel.selectPhoto(image)

        viewModel.onPhotoClick()
        viewModel.onEditConfirmed(image, croppedMonoEdit)
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals(2, postCreationRepository.prepareCount)
        assertEquals(1, postCreationRepository.preparations.size)
        assertEquals(croppedMonoEdit, postCreationRepository.preparedEdits.last())
        assertEquals(ImagePreparationStatus.Ready, viewModel.uiState.value.imagePreparationStatus)
    }

    @Test
    fun `편집을 바꾸지 않고 완료하면 다시 준비하지 않는다`() {
        val image = "content://media/photo/1"
        viewModel.selectPhoto(image, croppedMonoEdit)

        viewModel.onPhotoClick()
        viewModel.onEditConfirmed(image, croppedMonoEdit)

        assertEquals(1, postCreationRepository.prepareCount)
        assertTrue(postCreationRepository.discardedPreparations.isEmpty())
    }

    @Test
    fun `제출 결과에 편집 값을 함께 담는다`() = runTest {
        postCreationRepository.result = PostCreationResult.Success(
            PostCreation(
                postId = "post-id",
                topic = postCreationRepository.defaultTopic,
                moderationStatus = PostModerationStatus.VALIDATING,
            ),
        )
        viewModel.selectPhoto("content://media/photo/1", croppedMonoEdit)

        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(
            croppedMonoEdit,
            viewModel.uiState.value.completedSubmission
                ?.edit,
        )
    }

    @Test
    fun `카메라 사진 갤러리 저장 실패는 업로드 선택 상태를 유지하고 토스트로 알린다`() {
        val image = "content://media/photo/1"
        viewModel.selectPhoto(image)

        viewModel.onCapturedImageSaveFailed()

        assertSame(image, viewModel.uiState.value.selectedImage)
        assertTrue(viewModel.uiState.value.canSubmit)
        assertEquals(
            "사진을 갤러리에 저장하지 못했어요.",
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
    }

    @Test
    fun `사진 준비 중 제출하면 완료를 기다린 뒤 자동 제출한다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        postCreationRepository.prepareAwait = gate
        postCreationRepository.result = PostCreationResult.Success(
            PostCreation(
                postId = "post-id",
                topic = postCreationRepository.defaultTopic,
                moderationStatus = PostModerationStatus.VALIDATING,
            ),
        )

        viewModel.selectPhoto("content://media/photo/1")
        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(ImagePreparationStatus.Preparing, viewModel.uiState.value.imagePreparationStatus)
        assertTrue(viewModel.uiState.value.isSubmitting)
        assertEquals(0, postCreationRepository.callCount)

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals(1, postCreationRepository.callCount)
        assertTrue(viewModel.uiState.value.completedSubmission != null)
    }

    @Test
    fun `선제 준비 실패를 즉시 표시하고 제출로 다시 준비한다`() = runTest {
        postCreationRepository.prepareResults += PostImagePreparationResult.Failure(
            PostCreationFailure.NetworkUnavailable,
        )
        postCreationRepository.result = PostCreationResult.Success(
            PostCreation(
                postId = "post-id",
                topic = postCreationRepository.defaultTopic,
                moderationStatus = PostModerationStatus.VALIDATING,
            ),
        )
        viewModel.selectPhoto("content://media/photo/1")

        assertEquals(ImagePreparationStatus.Failed, viewModel.uiState.value.imagePreparationStatus)
        assertEquals(
            "네트워크 연결을 확인해 주세요.",
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
        assertTrue(viewModel.uiState.value.canSubmit)

        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(2, postCreationRepository.prepareCount)
        assertEquals(1, postCreationRepository.callCount)
        assertTrue(viewModel.uiState.value.completedSubmission != null)
    }

    @Test
    fun `사진을 교체하면 이전 준비 결과를 폐기한다`() {
        viewModel.selectPhoto("content://media/photo/1")
        val firstPreparation = postCreationRepository.preparations.single()

        viewModel.selectPhoto("content://media/photo/2")

        assertEquals(listOf(firstPreparation), postCreationRepository.discardedPreparations)
        assertEquals("content://media/photo/2", viewModel.uiState.value.selectedImage)
        assertEquals(2, postCreationRepository.prepareCount)
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
        assertEquals(0, postCreationRepository.callCount)
    }

    @Test
    fun `성공 결과는 실제 제출 정보와 함께 durable 상태가 된다`() = runTest {
        postCreationRepository.result = PostCreationResult.Success(
            PostCreation(
                postId = "post-id",
                topic = Topic(
                    id = "topic-id",
                    title = "바다",
                    date = LocalDate.of(2026, 8, 29),
                ),
                moderationStatus = PostModerationStatus.PENDING,
            ),
        )
        val image = "content://media/photo/1"
        viewModel.selectPhoto(image)
        viewModel.onAction(PhotoUploadUiAction.CaptionChanged("한낮의 다리"))

        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(
            PhotoUploadSubmission(
                imageModel = image,
                edit = PhotoEdit(),
                caption = "한낮의 다리",
                content = PhotoUploadSuccessContent(
                    date = LocalDate.of(2026, 8, 29),
                    topic = "바다",
                    moderationStatus = "PENDING",
                ),
            ),
            viewModel.uiState.value.completedSubmission,
        )
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `제출 완료 후 반복 탭은 repository를 다시 호출하지 않는다`() = runTest {
        postCreationRepository.result = PostCreationResult.Success(
            PostCreation(
                postId = "post-id",
                topic = Topic(
                    id = "topic-id",
                    title = "바다",
                    date = uploadTopicDate,
                ),
                moderationStatus = PostModerationStatus.PENDING,
            ),
        )
        viewModel.selectPhoto("content://media/photo/1")

        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)
        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(1, postCreationRepository.callCount)
        assertFalse(viewModel.uiState.value.canSubmit)
        assertTrue(viewModel.uiState.value.completedSubmission != null)
    }

    @Test
    fun `처리 중 반복 탭은 repository를 한 번만 호출한다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        postCreationRepository.await = gate
        viewModel.selectPhoto("content://media/photo/1")

        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)
        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(1, postCreationRepository.callCount)
        assertEquals(
            uploadTopicDate,
            postCreationRepository.requestedTopics
                .single()
                .date,
        )
        assertTrue(viewModel.uiState.value.isSubmitting)

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `자정이 지나도 업로드 화면 진입 시 고정한 주제 날짜로 제출한다`() = runTest {
        viewModel.selectPhoto("content://media/photo/1")

        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(listOf(uploadTopicDate), postCreationRepository.requestedTopicDates)
        assertEquals(
            uploadTopicDate,
            postCreationRepository.requestedTopics
                .single()
                .date,
        )
    }

    @Test
    fun `주제 선조회 실패 후 재시도하면 주제를 다시 조회하고 제출할 수 있다`() = runTest {
        val repository = FakePostCreationRepository().apply {
            topicResults.add(PostCreationTopicResult.Failure(PostCreationFailure.NetworkUnavailable))
            topicResults.add(PostCreationTopicResult.Success(defaultTopic))
            result = PostCreationResult.Success(
                PostCreation(
                    postId = "post-id",
                    topic = Topic(
                        id = "topic-id",
                        title = "바다",
                        date = uploadTopicDate,
                    ),
                    moderationStatus = PostModerationStatus.VALIDATING,
                ),
            )
        }
        val retryViewModel = PhotoUploadViewModel(repository, uploadTopicDate)
        assertEquals("네트워크 연결을 확인해 주세요.", retryViewModel.uiState.value.topicErrorMessage)

        retryViewModel.retryTopicLoad()
        retryViewModel.selectPhoto("content://media/photo/1")

        retryViewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(listOf(uploadTopicDate, uploadTopicDate), repository.requestedTopicDates)
        assertEquals(1, repository.callCount)
        assertTrue(retryViewModel.uiState.value.completedSubmission != null)
    }

    @Test
    fun `실패하면 입력을 유지하고 다시 제출할 수 있다`() = runTest {
        postCreationRepository.results.add(PostCreationResult.Failure(PostCreationFailure.NetworkUnavailable))
        postCreationRepository.results.add(
            PostCreationResult.Success(
                PostCreation(
                    postId = "post-id",
                    topic = Topic(
                        id = "topic-id",
                        title = "바다",
                        date = LocalDate.of(2026, 8, 29),
                    ),
                    moderationStatus = PostModerationStatus.VALIDATING,
                ),
            ),
        )
        val image = "content://media/photo/1"
        viewModel.selectPhoto(image)
        viewModel.onAction(PhotoUploadUiAction.CaptionChanged("제목"))
        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(image, viewModel.uiState.value.selectedImage)
        assertEquals("제목", viewModel.uiState.value.caption)
        assertEquals(
            "네트워크 연결을 확인해 주세요.",
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
        assertTrue(viewModel.uiState.value.canSubmit)

        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(2, postCreationRepository.callCount)
        assertTrue(viewModel.uiState.value.completedSubmission != null)
    }

    @Test
    fun `중복 제출 실패는 구체적인 메시지와 입력을 유지한다`() = runTest {
        postCreationRepository.result = PostCreationResult.Failure(PostCreationFailure.AlreadySubmitted)
        val image = "content://media/photo/1"
        viewModel.selectPhoto(image)
        viewModel.onAction(PhotoUploadUiAction.CaptionChanged("제목"))
        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(
            "이미 이 주제에 전시한 사진이 있어요.",
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
        assertEquals(image, viewModel.uiState.value.selectedImage)
        assertEquals("제목", viewModel.uiState.value.caption)
    }

    @Test
    fun `닫힌 주제 실패는 주제 변경 메시지와 입력을 유지한다`() = runTest {
        postCreationRepository.result = PostCreationResult.Failure(PostCreationFailure.TopicNotOpen)
        val image = "content://media/photo/1"
        viewModel.selectPhoto(image)
        viewModel.onAction(PhotoUploadUiAction.CaptionChanged("제목"))
        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(
            "주제가 변경되어 전시할 수 없어요.",
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
        assertEquals(image, viewModel.uiState.value.selectedImage)
        assertEquals("제목", viewModel.uiState.value.caption)
    }

    @Test
    fun `401은 재인증 effect를 보내고 입력은 유지한다`() = runTest {
        postCreationRepository.result = PostCreationResult.Failure(
            PostCreationFailure.ReauthenticationRequired,
        )
        val image = "content://media/photo/1"
        viewModel.selectPhoto(image)

        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)

        assertEquals(PhotoUploadUiEvent.ReauthenticationRequired, viewModel.uiEvent.first())
        assertEquals(image, viewModel.uiState.value.selectedImage)
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `초기화하면 사진과 캡션을 제거한다`() {
        viewModel.selectPhoto("content://media/photo/1")
        val preparation = postCreationRepository.preparations.single()
        viewModel.onAction(PhotoUploadUiAction.CaptionChanged("오늘의 사진"))

        viewModel.reset()

        assertEquals(
            PhotoUploadUiState(topicTitle = postCreationRepository.defaultTopic.title),
            viewModel.uiState.value,
        )
        assertEquals(listOf(preparation), postCreationRepository.discardedPreparations)
    }

    @Test
    fun `주제 조회 대기 중 이미지 준비가 실패해도 주제 로딩 상태를 유지한다`() = runTest {
        val topicGate = CompletableDeferred<Unit>()
        val repository = FakePostCreationRepository().apply {
            topicAwait = topicGate
            prepareResults += PostImagePreparationResult.Failure(PostCreationFailure.NetworkUnavailable)
        }
        val pendingViewModel = PhotoUploadViewModel(repository, uploadTopicDate)
        pendingViewModel.selectPhoto("content://media/photo/1")

        assertEquals(
            "네트워크 연결을 확인해 주세요.",
            (pendingViewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
        assertTrue(pendingViewModel.uiState.value.isTopicLoading)
        assertFalse(pendingViewModel.uiState.value.canSubmit)
        assertEquals(listOf(uploadTopicDate), repository.requestedTopicDates)
    }
}

private val croppedMonoEdit = PhotoEdit(
    quarterTurns = 1,
    crop = CropWindow(centerX = 0.4f, centerY = 0.5f, width = 0.6f, height = 0.8f),
    filter = PhotoFilter.Mono,
)

private fun PhotoUploadViewModel.selectPhoto(
    image: String,
    edit: PhotoEdit = PhotoEdit(),
) {
    onImageSelected(image)
    onEditConfirmed(image, edit)
}

private class FakePostCreationRepository : PostCreationRepository {
    val defaultTopic = Topic(
        id = "topic-id",
        title = "바다",
        date = LocalDate.of(2026, 8, 29),
    )
    var topicResult: PostCreationTopicResult = PostCreationTopicResult.Success(defaultTopic)
    var cachedTopic: Topic? = null
    val topicResults = ArrayDeque<PostCreationTopicResult>()
    var topicAwait: CompletableDeferred<Unit>? = null
    val prepareResults = ArrayDeque<PostImagePreparationResult>()
    var prepareAwait: CompletableDeferred<Unit>? = null
    var result: PostCreationResult = PostCreationResult.Failure(
        PostCreationFailure.NetworkUnavailable,
    )
    val results = ArrayDeque<PostCreationResult>()
    var await: CompletableDeferred<Unit>? = null
    var callCount = 0
    var prepareCount = 0
    val requestedTopicDates = mutableListOf<LocalDate>()
    val requestedTopics = mutableListOf<Topic>()
    val preparations = mutableListOf<PostImagePreparation>()
    val preparedEdits = mutableListOf<PhotoEdit>()
    val discardedPreparations = mutableListOf<PostImagePreparation>()

    override fun getCachedCreationTopic(topicDate: LocalDate): Topic? = cachedTopic?.takeIf { it.date == topicDate }

    override suspend fun getCreationTopic(topicDate: LocalDate): PostCreationTopicResult {
        requestedTopicDates += topicDate
        topicAwait?.await()
        return if (topicResults.isEmpty()) topicResult else topicResults.removeFirst()
    }

    override suspend fun prepareImage(
        imageUri: String,
        edit: PhotoEdit,
    ): PostImagePreparationResult {
        prepareCount++
        preparedEdits += edit
        prepareAwait?.await()
        if (prepareResults.isNotEmpty()) return prepareResults.removeFirst()
        val preparation = PostImagePreparation("preparation-$prepareCount")
        preparations += preparation
        return PostImagePreparationResult.Success(preparation)
    }

    override fun discardPreparedImage(preparation: PostImagePreparation) {
        discardedPreparations += preparation
    }

    override suspend fun createPost(
        preparation: PostImagePreparation,
        title: String?,
        topic: Topic,
    ): PostCreationResult {
        callCount++
        requestedTopics += topic
        await?.await()
        return if (results.isEmpty()) result else results.removeFirst()
    }
}
