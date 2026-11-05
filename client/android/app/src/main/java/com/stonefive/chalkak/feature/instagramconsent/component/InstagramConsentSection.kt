package com.stonefive.chalkak.feature.instagramconsent.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.stonefive.chalkak.core.designsystem.component.input.ChalkakTextField
import com.stonefive.chalkak.core.designsystem.component.toggle.ChalkakSwitch
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.instagramconsent.INSTAGRAM_ID_MAX_LENGTH
import com.stonefive.chalkak.feature.instagramconsent.InstagramConsentInputState

/**
 * 전시하기 화면과 게시물 수정 시트의 이야기 칸 아래에 두는 "공식 인스타그램 소개 허용" 묶음(이슈 #939).
 *
 * - 위: 제목 "작가님의 작품을 찰칵 인스타그램에서 만날 수 있게 해도 될까요?", 오른쪽 스위치. 행 전체를 눌러도 켜고 끈다.
 *   제목은 스위치 왼쪽 남은 폭 안에서 줄바꿈되어 스위치와 겹치지 않는다.
 * - 스위치가 켜지면: 앞에 `@`가 붙은 ID 칸과, 그 아래 안내 "작성하신 계정을 올릴 때 태그해드릴게요(선택)".
 *   서버가 ID를 거절했으면([InstagramConsentInputState.instagramIdError]) 안내 자리에 오류 색으로 오류 문구를 보여 준다.
 * - 스위치가 꺼지면 ID 칸과 안내를 감춘다. 쓰던 ID는 상태에 남는다.
 *
 * 입력은 [state]로 받기만 하고, 바뀐 값은 [onAgreedChange]·[onInstagramIdChange]로 올린다.
 * ID 칸 포커스는 [onInstagramIdFocusChange]로 알려, 맨 아래 칸일 때 부모가 스크롤을 따라 내릴 수 있게 한다.
 */
@Composable
fun InstagramConsentSection(
    state: InstagramConsentInputState,
    onAgreedChange: (Boolean) -> Unit,
    onInstagramIdChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onInstagramIdFocusChange: (Boolean) -> Unit = {},
) {
    Column(modifier = modifier) {
        InstagramConsentToggleRow(
            checked = state.agreed,
            enabled = enabled,
            onCheckedChange = onAgreedChange,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.agreed) {
            Spacer(modifier = Modifier.height(ChalkakTheme.spacing.sm))
            ChalkakTextField(
                value = state.instagramId,
                onValueChange = onInstagramIdChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onInstagramIdFocusChange(it.isFocused) }
                    .testTag(INSTAGRAM_CONSENT_ID_TAG),
                placeholder = "인스타그램 ID",
                enabled = enabled,
                textStyle = ChalkakTheme.typography.subheadline,
                singleLine = true,
                maxLength = INSTAGRAM_ID_MAX_LENGTH,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                prefix = "@",
            )
            Spacer(modifier = Modifier.height(ChalkakTheme.spacing.xs))
            val error = state.instagramIdError
            Text(
                text = error ?: "작성하신 계정을 올릴 때 태그해드릴게요(선택)",
                modifier = Modifier.testTag(INSTAGRAM_CONSENT_HELPER_TAG),
                color = if (error != null) ChalkakTheme.colors.error else ChalkakTheme.colors.textMuted,
                style = ChalkakTheme.typography.footnote,
            )
        }
    }
}

@Composable
private fun InstagramConsentToggleRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            ).testTag(INSTAGRAM_CONSENT_SWITCH_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // weight(1f)로 스위치 왼쪽 남은 폭만 쓰게 해, 긴 제목이 스위치 아래로 밀리지 않고 줄바꿈된다.
        Text(
            text = "작가님의 작품을 찰칵 인스타그램에서 만날 수 있게 해도 될까요?",
            modifier = Modifier.weight(1f),
            color = ChalkakTheme.colors.textPrimary,
            style = ChalkakTheme.typography.caption,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.width(ChalkakTheme.spacing.md))
        ChalkakSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun InstagramConsentSectionOffPreview() {
    ChalkakTheme {
        InstagramConsentSection(
            state = InstagramConsentInputState(),
            onAgreedChange = {},
            onInstagramIdChange = {},
            modifier = Modifier
                .background(ChalkakTheme.colors.background)
                .padding(ChalkakTheme.spacing.xl),
        )
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun InstagramConsentSectionOnPreview() {
    ChalkakTheme {
        InstagramConsentSection(
            state = InstagramConsentInputState(agreed = true, instagramId = "chalkak.daily"),
            onAgreedChange = {},
            onInstagramIdChange = {},
            modifier = Modifier
                .background(ChalkakTheme.colors.background)
                .padding(ChalkakTheme.spacing.xl),
        )
    }
}

@Preview(showBackground = true, widthDp = 402)
@Composable
private fun InstagramConsentSectionErrorPreview() {
    ChalkakTheme {
        InstagramConsentSection(
            state = InstagramConsentInputState(
                agreed = true,
                instagramId = "chalkak-daily",
                instagramIdError = "영문, 숫자, 마침표(.), 밑줄(_)만 쓸 수 있어요",
            ),
            onAgreedChange = {},
            onInstagramIdChange = {},
            modifier = Modifier
                .background(ChalkakTheme.colors.background)
                .padding(ChalkakTheme.spacing.xl),
        )
    }
}

const val INSTAGRAM_CONSENT_SWITCH_TAG = "instagram_consent_switch"
const val INSTAGRAM_CONSENT_ID_TAG = "instagram_consent_id"
const val INSTAGRAM_CONSENT_HELPER_TAG = "instagram_consent_helper"
