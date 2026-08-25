package com.stonefive.chalkak.feature.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.component.dialog.CONFIRM_BUTTON_TEST_TAG
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `회원은 사인과 계정 관리 메뉴를 본다`() {
        setSettingsContent(
            SettingsUiState(
                isLoggedIn = true,
                signatureModel = R.drawable.preview_signature,
                versionName = "1.0",
            ),
        )

        composeRule.onNodeWithText("사인 재설정").assertIsDisplayed()
        composeRule.onNodeWithText("로그아웃").assertIsDisplayed()
        composeRule.onNodeWithText("회원탈퇴").assertIsDisplayed()
        composeRule.onAllNodesWithText("로그인").assertCountEquals(0)
    }

    @Test
    fun `비회원은 로그인 버튼만 본다`() {
        var loginClicked = false
        setSettingsContent(
            uiState = SettingsUiState(
                versionName = "1.0",
            ),
            onLoginClick = { loginClicked = true },
        )

        composeRule.onNodeWithText("로그인").performClick()

        assertTrue(loginClicked)
        composeRule.onAllNodesWithText("사인 재설정").assertCountEquals(0)
        composeRule.onAllNodesWithText("로그아웃").assertCountEquals(0)
        composeRule.onAllNodesWithText("회원탈퇴").assertCountEquals(0)
    }

    @Test
    fun `개인정보처리방침 행을 누르면 개인정보처리방침 콜백을 호출한다`() {
        var privacyPolicyClickCount = 0
        setSettingsContent(
            uiState = SettingsUiState(versionName = "1.0"),
            onPrivacyPolicyClick = { privacyPolicyClickCount++ },
        )

        composeRule.onNodeWithText("개인정보처리방침").performClick()

        assertEquals(1, privacyPolicyClickCount)
    }

    @Test
    fun `이용약관 행을 누르면 이용약관 콜백을 호출한다`() {
        var termsClickCount = 0
        setSettingsContent(
            uiState = SettingsUiState(versionName = "1.0"),
            onTermsClick = { termsClickCount++ },
        )

        composeRule.onNodeWithText("이용약관").performClick()

        assertEquals(1, termsClickCount)
    }

    @Test
    fun `로그아웃 확인 다이얼로그를 표시하고 확인할 수 있다`() {
        var confirmed = false
        setSettingsContent(
            uiState = SettingsUiState(
                isLoggedIn = true,
                versionName = "1.0",
                accountDialog = SettingsAccountDialog.LOGOUT,
            ),
            onAccountDialogConfirm = { confirmed = true },
        )

        composeRule.onNodeWithText("정말 로그아웃 하시겠습니까?").assertIsDisplayed()
        composeRule.onNodeWithTag(CONFIRM_BUTTON_TEST_TAG).performClick()

        assertTrue(confirmed)
    }

    @Test
    fun `회원탈퇴 확인 다이얼로그를 취소할 수 있다`() {
        var dismissed = false
        setSettingsContent(
            uiState = SettingsUiState(
                isLoggedIn = true,
                versionName = "1.0",
                accountDialog = SettingsAccountDialog.WITHDRAW,
            ),
            onAccountDialogDismiss = { dismissed = true },
        )

        composeRule.onNodeWithText("정말 회원탈퇴 하시겠습니까?").assertIsDisplayed()
        composeRule.onNodeWithText("취소").performClick()

        assertTrue(dismissed)
    }

    private fun setSettingsContent(
        uiState: SettingsUiState,
        onLoginClick: () -> Unit = {},
        onPrivacyPolicyClick: () -> Unit = {},
        onTermsClick: () -> Unit = {},
        onAccountDialogConfirm: () -> Unit = {},
        onAccountDialogDismiss: () -> Unit = {},
    ) {
        composeRule.setContent {
            ChalkakTheme {
                SettingsScreen(
                    uiState = uiState,
                    onLoginClick = onLoginClick,
                    onChangeSignatureClick = {},
                    onPrivacyPolicyClick = onPrivacyPolicyClick,
                    onTermsClick = onTermsClick,
                    onLogoutClick = {},
                    onWithdrawClick = {},
                    onAccountDialogConfirm = onAccountDialogConfirm,
                    onAccountDialogDismiss = onAccountDialogDismiss,
                    onNavigateToBottomBar = {},
                    onAddClick = {},
                )
            }
        }
    }
}
