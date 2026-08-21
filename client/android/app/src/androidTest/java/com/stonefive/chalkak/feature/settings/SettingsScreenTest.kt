package com.stonefive.chalkak.feature.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
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

    private fun setSettingsContent(
        uiState: SettingsUiState,
        onLoginClick: () -> Unit = {},
    ) {
        composeRule.setContent {
            ChalkakTheme {
                SettingsScreen(
                    uiState = uiState,
                    onLoginClick = onLoginClick,
                    onChangeSignatureClick = {},
                    onPrivacyPolicyClick = {},
                    onTermsClick = {},
                    onLogoutClick = {},
                    onWithdrawClick = {},
                    onNavigateToBottomBar = {},
                    onAddClick = {},
                )
            }
        }
    }
}
