package com.stonefive.chalkak.feature.upload

import androidx.lifecycle.SavedStateHandle
import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.InstagramConsent
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.InstagramConsentPreferenceRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class InstagramConsentCacheTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `초안이 캐시보다 우선하고 초기화하면 계정 캐시를 적용한다`() = runTest {
        val cache = FakeInstagramConsentPreferences().apply { value = InstagramConsent(true, "cached") }
        val model = model(
            cache,
            SavedStateHandle(
                mapOf(
                    "upload_instagram_agreed" to false,
                    "upload_instagram_id" to "draft",
                ),
            ),
        )
        advanceUntilIdle()
        assertEquals("draft", model.uiState.value.instagramConsent.instagramId)
        assertEquals(false, model.uiState.value.instagramConsent.agreed)
        model.reset()
        advanceUntilIdle()
        assertEquals("cached", model.uiState.value.instagramConsent.instagramId)
        assertEquals(true, model.uiState.value.instagramConsent.agreed)
    }

    @Test
    fun `늦게 도착한 캐시는 사용자가 바꾼 입력을 덮지 않는다`() = runTest {
        val cache = FakeInstagramConsentPreferences().apply { readGate = CompletableDeferred() }
        val model = model(cache)
        model.onAction(PhotoUploadUiAction.InstagramIdChanged("typing"))
        cache.readGate!!.complete(InstagramConsent(true, "cached"))
        advanceUntilIdle()
        assertEquals("typing", model.uiState.value.instagramConsent.instagramId)
    }

    @Test
    fun `서버 성공 요청만 저장하고 OFF는 ID를 지우며 저장 실패에도 성공한다`() = runTest {
        val posts = FakePostCreationRepository()
        val cache = FakeInstagramConsentPreferences()
        val model = PhotoUploadViewModel(
            posts,
            LocalDate.of(2026, 8, 29),
            isPhotoEditEnabled = { false },
            sessionState = MutableStateFlow(UserSessionState.Authenticated("user-a")),
            instagramConsentPreferences = cache,
        )
        model.onImageSelected("content://photo")
        model.onAction(PhotoUploadUiAction.InstagramUploadAgreedChanged(true))
        model.onAction(PhotoUploadUiAction.InstagramIdChanged("typed"))
        model.onAction(PhotoUploadUiAction.SubmitClicked)
        advanceUntilIdle()
        assertEquals(emptyList<Pair<String, InstagramConsent>>(), cache.saved)
        posts.result = com.stonefive.chalkak.domain.model.PostCreationResult.Success(
            com.stonefive.chalkak.domain.model.PostCreation(
                "post",
                posts.defaultTopic,
                com.stonefive.chalkak.domain.model.PostModerationStatus.VALIDATING,
            ),
        )
        cache.failSave = true
        model.onAction(PhotoUploadUiAction.InstagramUploadAgreedChanged(false))
        model.onAction(PhotoUploadUiAction.SubmitClicked)
        advanceUntilIdle()
        assertEquals(listOf("user-a" to InstagramConsent.NotAgreed), cache.saved)
        org.junit.Assert
            .assertNotNull(model.uiState.value.completedSubmission)
    }

    @Test
    fun `캐시를 적용한 뒤 동의만 바꿔도 프로세스 복원 시 ID를 유지한다`() = runTest {
        val cache = FakeInstagramConsentPreferences().apply { value = InstagramConsent(true, "cached") }
        val handle = SavedStateHandle()
        val first = model(cache, handle)
        advanceUntilIdle()
        first.onAction(PhotoUploadUiAction.InstagramUploadAgreedChanged(false))
        val snapshot = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        cache.value = InstagramConsent(true, "new-cache")
        val restored = model(cache, snapshot)
        advanceUntilIdle()
        assertEquals("cached", restored.uiState.value.instagramConsent.instagramId)
        assertEquals(false, restored.uiState.value.instagramConsent.agreed)
    }

    @Test
    fun `제출 중 도착한 캐시는 요청과 화면의 동의를 바꾸지 않는다`() = runTest {
        val cache = FakeInstagramConsentPreferences().apply { readGate = CompletableDeferred() }
        val posts = FakePostCreationRepository().apply { await = CompletableDeferred() }
        val model = PhotoUploadViewModel(
            posts,
            LocalDate.of(2026, 8, 29),
            isPhotoEditEnabled = { false },
            sessionState = MutableStateFlow(UserSessionState.Authenticated("user-a")),
            instagramConsentPreferences = cache,
        )
        model.onImageSelected("content://photo")
        model.onAction(PhotoUploadUiAction.SubmitClicked)
        cache.readGate!!.complete(InstagramConsent(true, "late"))
        advanceUntilIdle()
        assertEquals(false, model.uiState.value.instagramConsent.agreed)
        assertEquals(listOf(InstagramConsent.NotAgreed), posts.requestedConsents)
        posts.await!!.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `요청 중 계정이 바뀌어도 성공한 요청 계정과 동의 스냅샷을 저장한다`() = runTest {
        val cache = FakeInstagramConsentPreferences()
        val posts = FakePostCreationRepository().apply {
            await = CompletableDeferred()
            result = com.stonefive.chalkak.domain.model.PostCreationResult.Success(
                com.stonefive.chalkak.domain.model.PostCreation(
                    "post",
                    defaultTopic,
                    com.stonefive.chalkak.domain.model.PostModerationStatus.VALIDATING,
                ),
            )
        }
        val session = MutableStateFlow<UserSessionState>(UserSessionState.Authenticated("user-a"))
        val model = PhotoUploadViewModel(
            posts,
            LocalDate.of(2026, 8, 29),
            isPhotoEditEnabled = { false },
            sessionState = session,
            instagramConsentPreferences = cache,
        )
        model.onImageSelected("content://photo")
        model.onAction(PhotoUploadUiAction.InstagramUploadAgreedChanged(true))
        model.onAction(PhotoUploadUiAction.InstagramIdChanged("account-a"))
        model.onAction(PhotoUploadUiAction.SubmitClicked)
        session.value = UserSessionState.Authenticated("user-b")
        posts.await!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("user-a" to InstagramConsent(true, "account-a")), cache.saved)
    }

    private fun model(
        cache: InstagramConsentPreferenceRepository,
        handle: SavedStateHandle = SavedStateHandle(),
    ) = PhotoUploadViewModel(
        FakePostCreationRepository(),
        LocalDate.of(2026, 8, 29),
        savedStateHandle = handle,
        sessionState = MutableStateFlow(UserSessionState.Authenticated("user-a")),
        instagramConsentPreferences = cache,
    )
}

internal class FakeInstagramConsentPreferences : InstagramConsentPreferenceRepository {
    var value: InstagramConsent? = null
    var readGate: CompletableDeferred<InstagramConsent?>? = null
    var failSave = false
    val saved = mutableListOf<Pair<String, InstagramConsent>>()
    override suspend fun get(userId: String): InstagramConsent? = readGate?.await() ?: value
    override suspend fun save(
        userId: String,
        consent: InstagramConsent,
    ) {
        saved += userId to consent
        if (failSave) error("disk unavailable")
        value = consent
    }
    override suspend fun clear(userId: String) {
        value = null
    }
}
