package com.stonefive.chalkak.feature.upload

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.TodayPostModerationStatus
import com.stonefive.chalkak.domain.model.TodayPostStatus
import com.stonefive.chalkak.domain.model.TodayPostStatusFailure
import com.stonefive.chalkak.domain.model.TodayPostStatusResult
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.PhotoUploadEntryRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhotoUploadEntryGateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `작성하지 않았으면 서버 topicDate로 업로드 화면을 연다`() = runTest {
        val repository = FakePhotoUploadEntryRepository(
            result = TodayPostStatusResult.Success(
                TodayPostStatus(
                    topicDate = LocalDate.of(2026, 9, 9),
                    isPosted = false,
                    postId = null,
                    moderationStatus = null,
                ),
            ),
        )
        val viewModel = photoUploadEntryGateViewModel(repository)

        viewModel.openPhotoUpload()

        assertEquals(1, repository.requestCount)
        assertEquals(
            PhotoUploadEntryGateUiEvent.OpenPhotoUpload(LocalDate.of(2026, 9, 9)),
            viewModel.uiEvent.first(),
        )
        assertFalse(viewModel.uiState.value.isChecking)
    }

    @Test
    fun `이미 작성했으면 토스트를 띄우고 업로드 화면을 열지 않는다`() = runTest {
        val repository = FakePhotoUploadEntryRepository(
            result = TodayPostStatusResult.Success(
                TodayPostStatus(
                    topicDate = LocalDate.of(2026, 9, 9),
                    isPosted = true,
                    postId = "post-id",
                    moderationStatus = TodayPostModerationStatus.VALIDATING,
                ),
            ),
        )
        val viewModel = photoUploadEntryGateViewModel(repository)

        viewModel.openPhotoUpload()

        assertEquals(1, repository.requestCount)
        assertEquals(
            ALREADY_POSTED_MESSAGE,
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
        assertEquals(null, withTimeoutOrNull(1) { viewModel.uiEvent.first() })
    }

    @Test
    fun `참여 가능한 주제 없음과 정지 회원은 지정 토스트를 띄운다`() = runTest {
        listOf(
            TodayPostStatusFailure.NoOpenTopic to NO_OPEN_TOPIC_MESSAGE,
            TodayPostStatusFailure.Suspended to SUSPENDED_MEMBER_MESSAGE,
        ).forEach { (failure, expectedMessage) ->
            val viewModel = photoUploadEntryGateViewModel(
                FakePhotoUploadEntryRepository(TodayPostStatusResult.Failure(failure)),
            )

            viewModel.openPhotoUpload()

            assertEquals(
                expectedMessage,
                (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
            )
            assertEquals(null, withTimeoutOrNull(1) { viewModel.uiEvent.first() })
        }
    }

    @Test
    fun `기타 실패는 작성 여부 확인 실패 토스트를 띄운다`() = runTest {
        listOf(
            TodayPostStatusFailure.Network,
            TodayPostStatusFailure.InvalidResponse,
            TodayPostStatusFailure.Http(500),
        ).forEach { failure ->
            val viewModel = photoUploadEntryGateViewModel(
                FakePhotoUploadEntryRepository(TodayPostStatusResult.Failure(failure)),
            )

            viewModel.openPhotoUpload()

            assertEquals(
                TODAY_POST_STATUS_ERROR_MESSAGE,
                (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
            )
            assertEquals(null, withTimeoutOrNull(1) { viewModel.uiEvent.first() })
        }
    }

    @Test
    fun `401이면 로그인 이동 이벤트를 보낸다`() = runTest {
        val viewModel = photoUploadEntryGateViewModel(
            FakePhotoUploadEntryRepository(
                TodayPostStatusResult.Failure(TodayPostStatusFailure.ReauthenticationRequired),
            ),
        )

        viewModel.openPhotoUpload()

        assertEquals(PhotoUploadEntryGateUiEvent.NavigateToLogin, viewModel.uiEvent.first())
        assertFalse(viewModel.uiState.value.isChecking)
    }

    @Test
    fun `비회원은 API를 호출하지 않고 기존 로그인 필요 토스트를 띄운다`() = runTest {
        val repository = FakePhotoUploadEntryRepository()
        val viewModel = photoUploadEntryGateViewModel(
            repository = repository,
            sessionState = UserSessionState.Guest,
        )

        viewModel.openPhotoUpload()

        assertEquals(0, repository.requestCount)
        assertEquals(
            PHOTO_UPLOAD_LOGIN_REQUIRED_MESSAGE,
            (viewModel.uiState.value.pendingMessage as UiMessage.Toast).text,
        )
    }

    @Test
    fun `확인 중 반복 탭은 API를 한 번만 호출한다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakePhotoUploadEntryRepository().apply {
            await = gate
        }
        val viewModel = photoUploadEntryGateViewModel(repository)

        viewModel.openPhotoUpload()
        viewModel.openPhotoUpload()

        assertEquals(1, repository.requestCount)
        assertTrue(viewModel.uiState.value.isChecking)

        gate.complete(Unit)

        assertEquals(
            PhotoUploadEntryGateUiEvent.OpenPhotoUpload(LocalDate.of(2026, 9, 9)),
            viewModel.uiEvent.first(),
        )
    }
}

private fun photoUploadEntryGateViewModel(
    repository: PhotoUploadEntryRepository,
    sessionState: UserSessionState = UserSessionState.Authenticated("user-id"),
) = PhotoUploadEntryGateViewModel(
    repository = repository,
    sessionState = MutableStateFlow(sessionState),
)

private class FakePhotoUploadEntryRepository(
    var result: TodayPostStatusResult = TodayPostStatusResult.Success(
        TodayPostStatus(
            topicDate = LocalDate.of(2026, 9, 9),
            isPosted = false,
            postId = null,
            moderationStatus = null,
        ),
    ),
) : PhotoUploadEntryRepository {
    var requestCount = 0
    var await: CompletableDeferred<Unit>? = null

    override suspend fun getTodayPostStatus(): TodayPostStatusResult {
        requestCount++
        await?.await()
        return result
    }
}
