package com.stonefive.chalkak.debuglaunch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.stonefive.chalkak.BuildConfig
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.feed.FeedPostEditState
import com.stonefive.chalkak.feature.feed.component.FeedPostEditSheet
import com.stonefive.chalkak.feature.instagramconsent.InstagramConsentInputState
import com.stonefive.chalkak.feature.upload.PhotoUploadScreen
import com.stonefive.chalkak.feature.upload.PhotoUploadUiAction
import com.stonefive.chalkak.feature.upload.PhotoUploadUiState

/**
 * 인스타그램 게시 동의(#953) 화면
 * - upload: 사진을 고른 작성 화면(동의 꺼짐)
 * - edit: 게시물 수정 시트(동의 켜짐, ID chalkak.daily). 피드 상세가 아니라 빈 바탕 위에 띄운다
 * - upload-error, edit-error: 서버가 ID 형식을 거절한 뒤(동의 켜짐, ID chalkak-daily, ID 칸 아래 오류)
 */
internal val instagramConsentDebugScreens: Map<String, @Composable () -> Unit> = mapOf(
    "upload" to { DebugUpload() },
    "upload-error" to { DebugUpload(rejectedIdConsent()) },
    "edit" to { DebugEditSheet() },
    "edit-error" to { DebugEditSheet(rejectedIdConsent()) },
)

/** ViewModel이 서버 거절을 받았을 때와 같은 함수로 만든다. */
private fun rejectedIdConsent(): InstagramConsentInputState =
    InstagramConsentInputState(agreed = true, instagramId = "chalkak-daily").withInvalidInstagramId()

@Composable
private fun DebugUpload(consent: InstagramConsentInputState = InstagramConsentInputState()) {
    var state by remember {
        mutableStateOf(
            PhotoUploadUiState(
                selectedImage = "android.resource://${BuildConfig.APPLICATION_ID}/${R.drawable.preview_photo}",
                topicTitle = "하늘",
                caption = "노을 진 하늘",
                description = "퇴근길에 만난 노을을 담았어요.",
                instagramConsent = consent,
            ),
        )
    }
    PhotoUploadScreen(
        uiState = state,
        onAction = { action ->
            state = when (action) {
                is PhotoUploadUiAction.CaptionChanged -> state.copy(caption = action.caption)

                is PhotoUploadUiAction.DescriptionChanged -> state.copy(description = action.description)

                is PhotoUploadUiAction.InstagramUploadAgreedChanged ->
                    state.copy(instagramConsent = state.instagramConsent.withAgreed(action.agreed))

                is PhotoUploadUiAction.InstagramIdChanged ->
                    state.copy(instagramConsent = state.instagramConsent.withInstagramId(action.instagramId))

                else -> state
            }
        },
    )
}

@Composable
private fun DebugEditSheet(
    consent: InstagramConsentInputState = InstagramConsentInputState(agreed = true, instagramId = "chalkak.daily"),
) {
    var edit by remember {
        mutableStateOf(
            FeedPostEditState(
                title = "늦은 오후의 골목",
                description = "퇴근길에 빛이 벽을 타고 내려오는 걸 보고 멈춰 섰어요.",
                instagramConsent = consent,
            ),
        )
    }
    Box(Modifier.fillMaxSize().background(ChalkakTheme.colors.background)) {
        FeedPostEditSheet(
            edit = edit,
            onTitleChange = { edit = edit.copy(title = it) },
            onDescriptionChange = { edit = edit.copy(description = it) },
            onSave = {},
            onDismiss = {},
            onInstagramUploadAgreedChange = {
                edit = edit.copy(
                    instagramConsent = edit.instagramConsent.withAgreed(it),
                )
            },
            onInstagramIdChange = { edit = edit.copy(instagramConsent = edit.instagramConsent.withInstagramId(it)) },
        )
    }
}
