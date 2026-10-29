package com.stonefive.chalkak.feature.upload

import androidx.lifecycle.SavedStateHandle
import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.PostCreation
import com.stonefive.chalkak.domain.model.PostCreationFailure
import com.stonefive.chalkak.domain.model.PostCreationResult
import com.stonefive.chalkak.domain.model.PostCreationTopicResult
import com.stonefive.chalkak.domain.model.PostImagePreparation
import com.stonefive.chalkak.domain.model.PostImagePreparationResult
import com.stonefive.chalkak.domain.model.PostModerationStatus
import com.stonefive.chalkak.domain.model.Topic
import com.stonefive.chalkak.domain.model.photoedit.CropWindow
import com.stonefive.chalkak.domain.model.photoedit.PhotoAdjustment
import com.stonefive.chalkak.domain.model.photoedit.PhotoAdjustmentLayers
import com.stonefive.chalkak.domain.model.photoedit.PhotoAdjustments
import com.stonefive.chalkak.domain.model.photoedit.PhotoEdit
import com.stonefive.chalkak.domain.model.photoedit.PhotoFilter
import com.stonefive.chalkak.domain.repository.PostCreationRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
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
    fun `원격 설정으로 사진 편집이 꺼져 있으면 편집 화면 없이 원본을 고른다`() = runTest {
        val viewModel = PhotoUploadViewModel(
            postCreationRepository,
            uploadTopicDate,
            isPhotoEditEnabled = { false },
        )
        val image = "content://media/photo/1"

        viewModel.onImageSelected(image)

        assertEquals(null, viewModel.uiState.value.editingPhoto)
        assertEquals(image, viewModel.uiState.value.selectedImage)
        assertEquals(PhotoEdit(), viewModel.uiState.value.selectedEdit)

        viewModel.onPhotoClick()

        assertEquals(null, viewModel.uiState.value.editingPhoto)
    }

    @Test
    fun `사진 편집이 꺼져 있어도 선택한 원본과 제목을 복원하고 자동 제출하지 않는다`() {
        val handle = SavedStateHandle()
        val original = PhotoUploadViewModel(
            postCreationRepository,
            uploadTopicDate,
            isPhotoEditEnabled = { false },
            savedStateHandle = handle,
        )
        original.onImageSelected("content://original")
        original.onAction(PhotoUploadUiAction.CaptionChanged("바다"))

        val snapshot = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val restored = PhotoUploadViewModel(
            postCreationRepository,
            uploadTopicDate,
            isPhotoEditEnabled = { false },
            savedStateHandle = snapshot,
        )

        assertEquals("content://original", restored.uiState.value.selectedImage)
        assertEquals("바다", restored.uiState.value.caption)
        assertEquals(PhotoEdit(), restored.uiState.value.selectedEdit)
        assertEquals(PhotoEdit(), postCreationRepository.preparedEdits.last())
        assertEquals(ImagePreparationStatus.Ready, restored.uiState.value.imagePreparationStatus)
        assertEquals(null, restored.uiState.value.editingPhoto)
        assertFalse(restored.uiState.value.isSubmitting)
        assertEquals(0, postCreationRepository.callCount)
    }

    @Test
    fun `사진 편집을 끈 뒤 복원해도 확정한 편집은 유지하고 편집기를 다시 열지 않는다`() {
        val handle = SavedStateHandle()
        val original = PhotoUploadViewModel(postCreationRepository, uploadTopicDate, savedStateHandle = handle)
        original.selectPhoto("content://edited", croppedMonoEdit)

        val snapshot = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val restored = PhotoUploadViewModel(
            postCreationRepository,
            uploadTopicDate,
            isPhotoEditEnabled = { false },
            savedStateHandle = snapshot,
        )
        restored.onPhotoClick()

        assertEquals("content://edited", restored.uiState.value.selectedImage)
        assertEquals(croppedMonoEdit, restored.uiState.value.selectedEdit)
        assertEquals(croppedMonoEdit, postCreationRepository.preparedEdits.last())
        assertEquals(null, restored.uiState.value.editingPhoto)
        assertEquals(ImagePreparationStatus.Ready, restored.uiState.value.imagePreparationStatus)
        assertFalse(restored.uiState.value.isSubmitting)
        assertEquals(0, postCreationRepository.callCount)
    }

    @Test
    fun `저장된 사진을 다시 준비하되 자동 제출하지 않는다`() {
        val image = "content://media/photo/restored"
        val handle = SavedStateHandle(mapOf("upload_selected_image" to image, "upload_caption" to "제목"))
        val restored = PhotoUploadViewModel(postCreationRepository, uploadTopicDate, savedStateHandle = handle)

        assertEquals(image, restored.uiState.value.selectedImage)
        assertEquals("제목", restored.uiState.value.caption)
        assertEquals(listOf(image), postCreationRepository.preparedImageUris)
        assertEquals(listOf(PhotoEdit()), postCreationRepository.preparedEdits)
        assertEquals(ImagePreparationStatus.Ready, restored.uiState.value.imagePreparationStatus)
        assertFalse(restored.uiState.value.isSubmitting)
        assertEquals(0, postCreationRepository.callCount)
    }

    @Test
    fun `저장된 편집의 필터를 알 수 없으면 사진 재선택을 안내한다`() {
        assertInvalidRestoredEdit(croppedMonoEdit.toSavedState().apply { put("filter", "Unknown") })
    }

    @Test
    fun `저장된 편집에 필수 값이 없으면 사진 재선택을 안내한다`() {
        assertInvalidRestoredEdit(croppedMonoEdit.toSavedState().apply { remove("cropWidth") })
    }

    private fun assertInvalidRestoredEdit(edit: HashMap<String, String>) {
        val handle = SavedStateHandle(
            mapOf(
                "upload_selected_image" to "content://edited",
                "upload_selected_edit" to edit,
                "upload_caption" to "작성 중",
            ),
        )
        val restored = PhotoUploadViewModel(postCreationRepository, uploadTopicDate, savedStateHandle = handle)

        assertEquals("작성 중", restored.uiState.value.caption)
        assertEquals("작성 중", handle.get<String>("upload_caption"))
        assertEquals(null, restored.uiState.value.selectedImage)
        assertFalse(handle.contains("upload_selected_image"))
        assertFalse(handle.contains("upload_selected_edit"))
        assertEquals(0, postCreationRepository.prepareCount)
        assertEquals(0, postCreationRepository.callCount)
        assertFalse(restored.uiState.value.isSubmitting)
        assertFalse(restored.uiState.value.canSubmit)
        assertEquals(
            "사진 편집 정보를 복원하지 못했어요. 사진을 다시 선택해 주세요.",
            (restored.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
    }

    @Test
    fun `선택한 사진을 저장하고 초기화하면 복원하지 않는다`() {
        val handle = SavedStateHandle()
        val original = PhotoUploadViewModel(postCreationRepository, uploadTopicDate, savedStateHandle = handle)
        original.selectPhoto("content://media/photo/1")
        original.selectPhoto("content://media/photo/2")

        val snapshot = mapOf("upload_selected_image" to handle.get<String>("upload_selected_image"))
        val restored = PhotoUploadViewModel(
            postCreationRepository,
            uploadTopicDate,
            savedStateHandle = SavedStateHandle(snapshot),
        )
        assertEquals("content://media/photo/2", restored.uiState.value.selectedImage)

        original.reset()
        assertFalse(handle.contains("upload_selected_image"))
        assertFalse(handle.contains("upload_selected_edit"))
        val prepareCount = postCreationRepository.prepareCount
        val reopened = PhotoUploadViewModel(postCreationRepository, uploadTopicDate, savedStateHandle = handle)
        assertEquals(null, reopened.uiState.value.selectedImage)
        assertEquals(prepareCount, postCreationRepository.prepareCount)
    }

    @Test
    fun `복원한 사진을 읽지 못해도 제목을 유지하고 실패를 표시한다`() {
        postCreationRepository.prepareResults += PostImagePreparationResult.Failure(
            PostCreationFailure.ImagePreparationFailed,
        )
        val handle = SavedStateHandle(mapOf("upload_selected_image" to "content://missing", "upload_caption" to "제목"))
        val restored = PhotoUploadViewModel(postCreationRepository, uploadTopicDate, savedStateHandle = handle)

        assertEquals("제목", restored.uiState.value.caption)
        assertEquals(ImagePreparationStatus.Failed, restored.uiState.value.imagePreparationStatus)
        assertEquals(
            "사진을 준비하지 못했어요. 다시 시도해 주세요.",
            (restored.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
        assertEquals(0, postCreationRepository.callCount)
    }

    @Test
    fun `복원 중 새 사진을 선택하면 이전 준비 결과가 덮어쓰지 않는다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        postCreationRepository.prepareAwait = gate
        val handle = SavedStateHandle(mapOf("upload_selected_image" to "content://old"))
        val restored = PhotoUploadViewModel(postCreationRepository, uploadTopicDate, savedStateHandle = handle)

        restored.selectPhoto("content://new", croppedMonoEdit)
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals("content://new", restored.uiState.value.selectedImage)
        assertEquals("content://new", handle.get<String>("upload_selected_image"))
        assertEquals(croppedMonoEdit, restored.uiState.value.selectedEdit)
        assertEquals(croppedMonoEdit, postCreationRepository.preparedEdits.last())
        assertEquals(ImagePreparationStatus.Ready, restored.uiState.value.imagePreparationStatus)
        assertEquals(0, postCreationRepository.callCount)
    }

    @Test
    fun `확정한 사진과 전체 편집 값을 복원해 다시 준비한다`() {
        val handle = SavedStateHandle()
        val original = PhotoUploadViewModel(postCreationRepository, uploadTopicDate, savedStateHandle = handle)
        val edit = croppedMonoEdit.copy(
            straightenDegrees = 2.5f,
            adjustmentLayers = PhotoAdjustmentLayers(
                automatic = PhotoAdjustments(exposure = 0.4f, warmth = -0.2f),
                intensity = 0.7f,
                manual = mapOf(PhotoAdjustment.Contrast to 0.3f, PhotoAdjustment.Shadows to -0.1f),
            ),
        )
        original.onAction(PhotoUploadUiAction.CaptionChanged("바다"))
        original.selectPhoto("content://edited", edit)

        val snapshot = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val restored = PhotoUploadViewModel(postCreationRepository, uploadTopicDate, savedStateHandle = snapshot)

        assertEquals("content://edited", restored.uiState.value.selectedImage)
        assertEquals("바다", restored.uiState.value.caption)
        assertEquals(edit, restored.uiState.value.selectedEdit)
        assertEquals(edit, postCreationRepository.preparedEdits.last())
        assertEquals(ImagePreparationStatus.Ready, restored.uiState.value.imagePreparationStatus)
        assertEquals(null, restored.uiState.value.editingPhoto)
        assertFalse(restored.uiState.value.isSubmitting)
        assertEquals(0, postCreationRepository.callCount)

        original.reset()
        assertFalse(handle.contains("upload_selected_edit"))
        assertFalse(handle.contains("upload_selected_image"))
        assertFalse(handle.contains("upload_caption"))
        val reopened = PhotoUploadViewModel(postCreationRepository, uploadTopicDate, savedStateHandle = handle)
        assertEquals(PhotoEdit(), reopened.uiState.value.selectedEdit)
        assertEquals(null, reopened.uiState.value.selectedImage)
        assertEquals("", reopened.uiState.value.caption)
    }

    @Test
    fun `복원 중 새 사진 편집을 취소하면 기존 사진과 편집의 준비를 유지한다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        postCreationRepository.prepareAwait = gate
        val handle = SavedStateHandle(
            mapOf(
                "upload_selected_image" to "content://old",
                "upload_selected_edit" to croppedMonoEdit.toSavedState(),
            ),
        )
        val restored = PhotoUploadViewModel(postCreationRepository, uploadTopicDate, savedStateHandle = handle)

        restored.onImageSelected("content://new")
        restored.onEditCanceled()
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals("content://old", restored.uiState.value.selectedImage)
        assertEquals(croppedMonoEdit, restored.uiState.value.selectedEdit)
        assertEquals("content://old", handle.get<String>("upload_selected_image"))
        assertEquals(croppedMonoEdit, handle.get<HashMap<String, String>>("upload_selected_edit")?.toPhotoEdit())
        assertEquals(listOf("content://old"), postCreationRepository.preparedImageUris)
        assertEquals(listOf(croppedMonoEdit), postCreationRepository.preparedEdits)
        assertEquals(ImagePreparationStatus.Ready, restored.uiState.value.imagePreparationStatus)
        assertEquals(0, postCreationRepository.callCount)
    }

    @Test
    fun `저장된 제목을 복원하되 자동 제출하지 않는다`() {
        val handle = SavedStateHandle(mapOf("upload_caption" to "작성 중"))
        val restored = PhotoUploadViewModel(postCreationRepository, uploadTopicDate, savedStateHandle = handle)

        assertEquals("작성 중", restored.uiState.value.caption)
        assertFalse(restored.uiState.value.isSubmitting)
        assertEquals(0, postCreationRepository.callCount)
    }

    @Test
    fun `변경한 제목을 저장하고 새 ViewModel에서 복원한다`() {
        val handle = SavedStateHandle()
        val original = PhotoUploadViewModel(postCreationRepository, uploadTopicDate, savedStateHandle = handle)
        original.onAction(PhotoUploadUiAction.CaptionChanged("바다"))

        val restoredHandle = SavedStateHandle(mapOf("upload_caption" to handle.get<String>("upload_caption")))
        val restored = PhotoUploadViewModel(postCreationRepository, uploadTopicDate, savedStateHandle = restoredHandle)

        assertEquals("바다", restored.uiState.value.caption)
        original.onAction(PhotoUploadUiAction.CaptionChanged(""))
        assertEquals("", handle.get<String>("upload_caption"))
    }

    @Test
    fun `초기화하면 저장한 제목도 제거한다`() {
        val handle = SavedStateHandle(mapOf("upload_caption" to "작성 중"))
        val original = PhotoUploadViewModel(postCreationRepository, uploadTopicDate, savedStateHandle = handle)

        original.reset()

        assertFalse(handle.contains("upload_caption"))
        val restored = PhotoUploadViewModel(postCreationRepository, uploadTopicDate, savedStateHandle = handle)
        assertEquals("", restored.uiState.value.caption)
    }

    @Test
    fun `입력 내용이 없거나 공백뿐이면 확인 없이 뒤로 간다`() = runTest {
        viewModel.onAction(PhotoUploadUiAction.BackClicked)
        assertEquals(PhotoUploadUiEvent.NavigateBack, viewModel.uiEvent.first())
        viewModel.onAction(PhotoUploadUiAction.CaptionChanged(" \n "))
        viewModel.onAction(PhotoUploadUiAction.BackClicked)
        assertEquals(PhotoUploadUiEvent.NavigateBack, viewModel.uiEvent.first())
        assertFalse(viewModel.uiState.value.showsDiscardConfirmation)
    }

    @Test
    fun `설명 작성 중 뒤로 가기를 취소하면 입력을 유지한다`() = runTest {
        viewModel.onAction(PhotoUploadUiAction.CaptionChanged("작품 제목"))
        viewModel.onAction(PhotoUploadUiAction.BackClicked)

        assertTrue(viewModel.uiState.value.showsDiscardConfirmation)
        assertEquals(null, withTimeoutOrNull(1) { viewModel.uiEvent.first() })
        viewModel.onDiscardDismissed()

        assertFalse(viewModel.uiState.value.showsDiscardConfirmation)
        assertEquals("작품 제목", viewModel.uiState.value.caption)
        assertEquals(null, withTimeoutOrNull(1) { viewModel.uiEvent.first() })
    }

    @Test
    fun `사진 선택 후에는 버리기를 확인해야 뒤로 간다`() = runTest {
        val image = "content://media/photo/1"
        viewModel.selectPhoto(image)
        viewModel.onAction(PhotoUploadUiAction.BackClicked)

        assertTrue(viewModel.uiState.value.showsDiscardConfirmation)
        assertEquals(image, viewModel.uiState.value.selectedImage)
        assertEquals(null, withTimeoutOrNull(1) { viewModel.uiEvent.first() })
        viewModel.onDiscardConfirmed()

        assertFalse(viewModel.uiState.value.showsDiscardConfirmation)
        assertEquals(PhotoUploadUiEvent.NavigateBack, viewModel.uiEvent.first())
        viewModel.onDiscardConfirmed()
        assertEquals(null, withTimeoutOrNull(1) { viewModel.uiEvent.first() })
    }

    @Test
    fun `전시 중에는 뒤로 가기로 제출을 중단하지 않는다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        postCreationRepository.await = gate
        viewModel.selectPhoto("content://media/photo/1")
        viewModel.onAction(PhotoUploadUiAction.SubmitClicked)
        assertTrue(viewModel.uiState.value.isSubmitting)

        viewModel.onAction(PhotoUploadUiAction.BackClicked)

        assertFalse(viewModel.uiState.value.showsDiscardConfirmation)
        assertEquals(null, withTimeoutOrNull(1) { viewModel.uiEvent.first() })
        gate.complete(Unit)
    }

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
    val preparedImageUris = mutableListOf<String>()
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
        preparedImageUris += imageUri
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
